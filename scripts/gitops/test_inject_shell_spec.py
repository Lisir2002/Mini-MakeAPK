#!/usr/bin/env python3
"""注入管线回归测试（QA 严过关 · 槽位加载内容双模式终验）。

覆盖 `scripts/gitops/inject_shell_spec.py` 的**本地页面注入**契约 —— 它是
"导出 APK 里点本地页面要有反应"的唯一保证面，之前是零自动化覆盖。

## 钉的四类契约

1. **写出**：相对路径子目录原样保留（`page/about.html` 落到 `mma_pages/page/about.html`），
   且壳引用的每个 path 都能在注入目录里**找到同名实体文件**（这正是导出壳
   `file:///android_asset/mma_pages/<path>` 会去读的路径）；
2. **构建期硬拦截**：引用了不存在的页面 / 非页面文件 / 没给 --project / 项目不是 html 型
   ——一律拒绝注入，**且不得把半成品写进工程**（写盘前先校验）；
3. **越界防护**：项目文件名含 `..` / 绝对路径 / `./` 前缀，一律跳过并报错，
   绝不写到 `assets/mma_pages` 之外；
4. **幂等与清理**：重新注入会清空上一项目的残留文件；不含本地页面的壳注入后
   `mma_pages` 为空；

另有一条**跨语言一致性**用例：脚本的 `LOCAL_PAGE_ASSET_DIR` 必须与导出壳
`InjectedLocalPages.LOCAL_PAGE_ASSET_DIR` 字面量一致 —— 两边各改一半本地页面就全废了。

运行：

    python3 -m unittest discover -s scripts/gitops -p "test_*.py" -v
    python3 scripts/gitops/test_inject_shell_spec.py        # 直接跑也行
"""
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import inject_shell_spec as inj  # noqa: E402  （须先补 sys.path）

TEMPLATE_KOTLIN = Path(__file__).resolve().parent.parent.parent / "template" / "src" / "main" / "java" / "com" / "make" / "apk" / "InjectedLocalPages.kt"


# ── 测试数据工厂 ────────────────────────────────────────────────────────────
def local_page_item(item_id: str, path: str, region_list: str = "items") -> dict:
    return {
        "id": item_id,
        "title": "条目",
        "iconRef": None,
        "action": {"type": "local_page", "path": path},
        "visible": True,
        "selected": False,
    }


def spec_with(*paths: str) -> dict:
    """四区分布挂若干 `local_page` 引用（尽量摊到不同区，顺带覆盖聚合逻辑）。"""
    regions = ["topBar", "topTabs", "bottomBar", "drawer"]
    spec: dict = {"specVersion": 1}
    for i, p in enumerate(paths):
        r = regions[i % len(regions)]
        key = "actions" if r == "topBar" else "items"
        spec.setdefault(r, {}).setdefault(key, []).append(local_page_item(f"it{i}", p, key))
    return spec


def html_project(files: list[tuple[str, str]], entry_file: str = "index.html") -> dict:
    """`RuntimeJson.encodeEntry(BundledHtml)` 的编码形态。"""
    return {
        "type": "html",
        "html": next((c for n, c in files if n == entry_file), ""),
        "entryFile": entry_file,
        "files": [{"name": n, "content": c} for n, c in files if n != entry_file],
    }


class InjectPipelineTestCase(unittest.TestCase):
    """每个用例都把脚本的写盘点重定向到临时目录 —— 绝不触碰真实工程。"""

    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.pages = self.root / "template" / "src" / "main" / "assets" / inj.LOCAL_PAGE_ASSET_DIR
        self.target = self.root / "template" / "src" / "main" / "res" / "raw" / "shell_spec.json"
        self.target.parent.mkdir(parents=True, exist_ok=True)

        self._saved = (inj.REPO_ROOT, inj.PAGES_TARGET, inj.TARGET)
        inj.REPO_ROOT = self.root
        inj.PAGES_TARGET = self.pages
        inj.TARGET = self.target

    def tearDown(self) -> None:
        # 门禁：还原前先确认本用例确实把写盘重定向到了临时目录（防止用例偷写真实工程）。
        self.assertEqual(self.pages, inj.PAGES_TARGET, "写盘点被还原过早，本用例可能污染了真实工程")
        inj.REPO_ROOT, inj.PAGES_TARGET, inj.TARGET = self._saved
        self.tmp.cleanup()

    # ── 辅助 ────────────────────────────────────────────────────────────
    def write(self, rel: str, obj: dict) -> Path:
        p = self.root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(obj, ensure_ascii=False), encoding="utf-8")
        return p

    def inject(self, spec_obj: dict, project_obj: dict | None) -> int:
        src = self.write("spec.json", spec_obj)
        proj = self.write("project.json", project_obj) if project_obj is not None else None
        return inj.cmd_inject(src, proj, build=False)

    def injected_files(self) -> list[str]:
        if not self.pages.is_dir():
            return []
        return sorted(
            str(p.relative_to(self.pages)).replace("\\", "/")
            for p in self.pages.rglob("*") if p.is_file()
        )

    def assert_rejected_without_trace(self, spec_obj: dict, project_obj: dict | None, why: str) -> None:
        rc = self.inject(spec_obj, project_obj)
        self.assertEqual(1, rc, f"{why}：必须拒绝注入（rc=1），实为 {rc}")
        self.assertFalse(self.target.exists(), f"{why}：不得把未通过校验的壳写进工程")


# ── ① 写出契约 ────────────────────────────────────────────────────────────
class WriteContractTest(InjectPipelineTestCase):

    def test_relative_subdirectory_is_preserved(self) -> None:
        """`page/about.html` 必须落成 `mma_pages/page/about.html`（子目录是这个需求的本体）。"""
        files = [
            ("index.html", "<html>入口</html>"),
            ("page/about.html", "<html>关于</html>"),
            ("css/style.css", "body{}"),
        ]
        self.assertEqual(0, self.inject(spec_with("index.html", "page/about.html"), html_project(files)))

        written = self.injected_files()
        self.assertIn("page/about.html", written, f"子目录未被保留：{written}")
        self.assertIn("css/style.css", written)
        self.assertIn("index.html", written)
        # 导出壳按 mma_pages/<path> 读：每个被引用的 path 都得能命中实体文件。
        for path in ("index.html", "page/about.html"):
            self.assertTrue((self.pages / path).is_file(), f"导出壳读不到的path: {path}")

    def test_content_is_written_verbatim(self) -> None:
        """内容按 UTF-8 原样落盘（不得截断 / 转码丢字符）。"""
        body = "<html>你好 · 关于我们</html>"
        self.assertEqual(0, self.inject(spec_with("page/about.html"), html_project([("page/about.html", body)])))
        self.assertEqual(body, (self.pages / "page/about.html").read_text(encoding="utf-8"))

    def test_entry_file_defaults_to_index_html(self) -> None:
        """项目 JSON 没写 entryFile 时回落 index.html，而非写空文件。"""
        proj = {"type": "html", "html": "<html>x</html>", "files": []}
        self.assertEqual(0, self.inject(spec_with("index.html"), proj))
        self.assertEqual(["index.html"], self.injected_files())

    def test_entry_content_wins_over_duplicate_in_files(self) -> None:
        """入口文件重复出现在 files 里时不重复供给，`html` 作为单一事实源胜出。"""
        proj = {
            "type": "html",
            "html": "<html>正文</html>",
            "entryFile": "index.html",
            "files": [{"name": "index.html", "content": "<html>脏数据</html>"}],
        }
        self.assertEqual(0, self.inject(spec_with("index.html"), proj))
        self.assertEqual(["index.html"], self.injected_files())
        self.assertEqual("<html>正文</html>", (self.pages / "index.html").read_text(encoding="utf-8"))

    def test_project_wrapper_form_is_accepted(self) -> None:
        """`{"entry": {...}}` 包装形态也要能读（app 导出产物可能包了一层）。"""
        proj = {"entry": html_project([("index.html", "<html>x</html>")])}
        self.assertEqual(0, self.inject(spec_with("index.html"), proj))
        self.assertEqual(["index.html"], self.injected_files())


# ── ② 构建期硬拦截 ────────────────────────────────────────────────────────
class BuildTimeRejectionTest(InjectPipelineTestCase):

    def test_referenced_page_missing_in_project_is_rejected(self) -> None:
        self.assert_rejected_without_trace(
            spec_with("index.html", "page/ghost.html"),
            html_project([("index.html", "<html>x</html>")]),
            "壳引用了项目里没有的页面",
        )

    def test_referenced_page_not_html_is_rejected(self) -> None:
        """.css/.js 不是可加载页面：预览 picker 也不会列它，这里再挡一道。"""
        for bad in ("css/style.css", "app.js", "logo.png", "README"):
            with self.subTest(bad=bad):
                files = [("index.html", "<html>x</html>"), (bad, "whatever")]
                self.assert_rejected_without_trace(
                    spec_with(bad), html_project(files), f"引用非页面文件 {bad}",
                )

    def test_missing_project_argument_is_rejected(self) -> None:
        """壳有本地页面却不给 --project = 必然导出「点了没反应」的 APK。"""
        self.assert_rejected_without_trace(spec_with("index.html"), None, "缺 --project")

    def test_url_project_with_local_pages_is_rejected(self) -> None:
        """URL 项目没有本地文件：引用本地页面的壳不能假装通过。"""
        self.assert_rejected_without_trace(
            spec_with("index.html"), {"type": "url", "url": "https://example.com"}, "URL 项目配本地页面壳",
        )

    def test_empty_local_page_path_is_rejected_by_spec_validation(self) -> None:
        """空 path（含纯空白）在壳校验阶段就该被拦 —— 它是「必然 resolves 不到」的输入。"""
        for bad in ("", "   "):
            with self.subTest(bad=bad):
                self.assert_rejected_without_trace(
                    spec_with(bad), html_project([("index.html", "<html>x</html>")]), f"空 path {bad!r}",
                )

    def test_broken_project_json_is_rejected(self) -> None:
        """项目 JSON 烂了要报错，而不是静默产出「没有本地页面的 APK」。"""
        src = self.write("spec.json", spec_with("index.html"))
        proj = self.root / "project.json"
        proj.write_text("{not json", encoding="utf-8")
        self.assertEqual(1, inj.cmd_inject(src, proj, build=False))


# ── ③ 越界防护 ────────────────────────────────────────────────────────────
class TraversalGuardTest(InjectPipelineTestCase):

    def assert_nothing_escaped(self, root: Path) -> None:
        """assets/mma_pages 之外不允许出现任何本次写入的文件。"""
        for created in root.rglob("*"):
            if created.is_file() and created.name in {"evil.html", "evil.txt"}:
                self.assertTrue(
                    str(created).replace("\\", "/").startswith(str(self.pages).replace("\\", "/") + "/"),
                    f"越界写入：{created}",
                )

    def test_parent_traversal_name_is_refused(self) -> None:
        files = [("index.html", "<html>x</html>"), ("../evil.html", "<html>pwn</html>")]
        rc = self.inject(spec_with("index.html"), html_project(files))
        self.assertEqual(1, rc, "含 `..` 的项目文件名必须报错")
        self.assert_nothing_escaped(self.root)

    def test_absolute_and_dot_slash_names_are_refused(self) -> None:
        for bad in ("/evil.html", "./evil.html", "docs/../../evil.html"):
            with self.subTest(bad=bad):
                files = [("index.html", "<html>x</html>"), (bad, "<html>pwn</html>")]
                self.assertEqual(1, self.inject(spec_with("index.html"), html_project(files)),
                                 f"{bad} 必须被拒")
                self.assert_nothing_escaped(self.root)

    def test_directory_entry_name_is_skipped(self) -> None:
        """目录条目（`docs/`，尾巴带 `/`）不是文件，安静跳过、不得落成实体。"""
        files: list[tuple[str, str]] = [("index.html", "<html>x</html>"), ("docs/", "")]
        self.assertEqual(0, self.inject(spec_with("index.html"), html_project(files)))
        self.assertFalse((self.pages / "docs").exists(), "目录条目不应落成实体")
        self.assertEqual(["index.html"], self.injected_files())

    def test_directory_path_referenced_by_shell_is_rejected(self) -> None:
        """壳引用了目录条目（`page/`）：它在 assets 里永远不是可加载页面，必须拦在构建期。"""
        files: list[tuple[str, str]] = [("index.html", "<html>x</html>"), ("page/about.html", "<html>y</html>")]
        self.assert_rejected_without_trace(
            spec_with("page/"), html_project(files), "壳引用目录 path",
        )

    def test_unsafe_name_never_reaches_assets_even_if_written(self) -> None:
        """兜底再验一次：`load_project_files` 直接调用时也不产出危险名。"""
        files, errs = inj.load_project_files(self.write("p.json", html_project([
            ("index.html", "<html>x</html>"), ("../../evil.html", "x"),
        ])))
        self.assertTrue(errs, "危险文件名必须留痕")
        self.assertEqual(["index.html"], [n for n, _ in files])


# ── ③b 文件名白名单（M1 健壮性 / M2 反斜杠越界）─────────────────────────────
class FileNameWhitelistTest(InjectPipelineTestCase):
    """不可信文件名必须在**入库**时就被拒 —— 走到写盘就是 traceback + 半写入态。"""

    def assert_name_refused(self, bad_name: str, why: str) -> None:
        files, errs = inj.load_project_files(self.write("p.json", html_project([
            ("index.html", "<html>x</html>"), (bad_name, "<html>pwn</html>"),
        ])))
        self.assertTrue(any(bad_name in e or "不安全" in e for e in errs), f"{why}：必须留痕，实为 {errs}")
        self.assertEqual(["index.html"], [n for n, _ in files], f"{why}：危险名不得入库")

    def test_nul_byte_name_is_refused(self) -> None:
        """NUL 字节会让 write_bytes 抛 ValueError —— 必须在入库前拦掉。"""
        self.assert_name_refused("ev\x00il.html", "含 NUL 的文件名")

    def test_control_character_name_is_refused(self) -> None:
        for bad in ("evil\n.html", "evil\r.html", "evil\x1b.html"):
            with self.subTest(bad=bad):
                self.assert_name_refused(bad, "含控制字符的文件名")

    def test_overlong_name_is_refused(self) -> None:
        """超长名撞 ENAMETOOLONG（OSError）—— 同样要在入库前拦掉。"""
        self.assert_name_refused("a" * 200 + ".html", f"长度超过 {inj.MAX_FILE_NAME_LENGTH} 的文件名")

    def test_name_at_length_limit_is_accepted(self) -> None:
        """边界：正好等于上限的名字是合法的，不能被误杀。"""
        name = "a" * (inj.MAX_FILE_NAME_LENGTH - len(".html")) + ".html"
        files, errs = inj.load_project_files(self.write("p.json", html_project([(name, "x")], entry_file=name)))
        self.assertEqual([], errs, f"上限内的名字被误杀：{errs}")
        self.assertEqual([name], [n for n, _ in files])

    def test_backslash_traversal_name_is_refused(self) -> None:
        """M2：`..\\..\\evil.html` 在 Windows 上会真的写出 assets/ 之外。

        Linux 上它只是个怪文件名（所以最容易漏），故按字符白名单直接拒绝，
        而不是依赖 `..` 段判定 —— 后者在 Windows 上根本看不到 `..` 这个段。
        """
        for bad in ("..\\..\\evil.html", "assets\\evil.html", "evil\\..\\x.html"):
            with self.subTest(bad=bad):
                self.assert_name_refused(bad, "含反斜杠的文件名")

    def test_backslash_name_never_escapes_assets(self) -> None:
        """端到端再验一次：带反斜杠越界名的项目，注入被拒且目录外无落点。"""
        files = [("index.html", "<html>x</html>"), ("..\\..\\evil.html", "<html>pwn</html>")]
        self.assertEqual(1, self.inject(spec_with("index.html"), html_project(files)),
                         "含反斜杠越界名必须被拒")
        for created in self.root.rglob("*"):
            if created.is_file() and created.name == "evil.html":
                self.fail(f"反斜杠越界写入：{created}")


class WriteRobustnessTest(InjectPipelineTestCase):
    """M1 后半：写盘异常必须转成 ::error:: + rc=1，且**不留半写入态**。"""

    def inject_capturing_stderr(self, spec_obj: dict, project_obj: dict) -> tuple[int, str]:
        src = self.write("spec.json", spec_obj)
        proj = self.write("project.json", project_obj)
        import io
        from contextlib import redirect_stderr
        buf = io.StringIO()
        with redirect_stderr(buf):
            rc = inj.cmd_inject(src, proj, build=False)
        return rc, buf.getvalue()

    def test_disk_error_becomes_error_line_not_traceback(self) -> None:
        """写盘炸了要给 ::error:: + rc=1，绝不能 traceback 退出（无 ::error:: = CI 上看不出原因）。"""
        bad = [("index.html", "<html>x</html>"), ("boom.html", "<html>y</html>")]
        real_stage = inj.stage_project_pages

        def exploding_stage(files):  # 替身模拟 OSError（磁盘满 / ENAMETOOLONG 之类）
            raise OSError(28, "No space left on device")

        inj.stage_project_pages = exploding_stage
        try:
            rc, err = self.inject_capturing_stderr(spec_with("index.html"), html_project(bad))
        finally:
            inj.stage_project_pages = real_stage

        self.assertEqual(1, rc, "写盘失败必须 rc=1")
        self.assertIn("::error::", err, f"必须输出 ::error:: 供 CI 高亮，实为：{err!r}")
        self.assertNotIn("Traceback", err, "不得抛 traceback")

    def test_no_half_written_state_on_disk_error(self) -> None:
        """写盘失败时壳不得已写、旧页面不得已被删 —— 否则 --print 会误导成"页面没注入"。

        先成功注入一套（壳 + 页面），再让下一次注入在写页面时炸掉：
        工程必须仍是**上一套完整的壳 + 页面**（可继续构建的状态）。
        """
        self.assertEqual(0, self.inject(spec_with("index.html"), html_project([
            ("index.html", "<html>v1</html>"),
        ])))
        prev_shell = self.target.read_text(encoding="utf-8")

        def exploding_stage(files):  # 替身：模拟磁盘满 / ENAMETOOLONG
            raise OSError(28, "No space left on device")

        real_stage = inj.stage_project_pages
        inj.stage_project_pages = exploding_stage
        try:
            rc, _err = self.inject_capturing_stderr(spec_with("index.html"), html_project([
                ("index.html", "<html>v2</html>"),
            ]))
        finally:
            inj.stage_project_pages = real_stage

        self.assertEqual(1, rc)
        self.assertEqual(prev_shell, self.target.read_text(encoding="utf-8"), "壳被改成了半写入态")
        self.assertEqual(["index.html"], self.injected_files(), "上一套页面被清成了空目录（半写入态）")
        self.assertEqual("<html>v1</html>", (self.pages / "index.html").read_text(encoding="utf-8"))

    def test_staging_dir_does_not_survive_success(self) -> None:
        """暂存目录用完即焚：留在 assets/ 下会被打进 APK（幽灵页面）。"""
        self.assertEqual(0, self.inject(spec_with("index.html"), html_project([("index.html", "<html>x</html>")])))
        leftovers = [p.name for p in self.root.rglob("*") if p.name.endswith(inj.PAGES_STAGING_SUFFIX)]
        self.assertEqual([], leftovers, f"暂存目录残留：{leftovers}")


# ── ④ 幂等与清理 ──────────────────────────────────────────────────────────
class IdempotencyTest(InjectPipelineTestCase):

    def test_reinject_clears_previous_project_residue(self) -> None:
        """换项目时必须清空：上一项目的残留文件混进新 APK 是典型的「幽灵页面」。"""
        self.inject(spec_with("index.html"), html_project([
            ("index.html", "<html>a</html>"), ("old/gone.html", "<html>旧</html>"),
        ]))
        self.assertIn("old/gone.html", self.injected_files())

        self.inject(spec_with("index.html"), html_project([("index.html", "<html>b</html>")]))
        self.assertEqual(["index.html"], self.injected_files(), "上一项目的残留页面未清除")

    def test_shell_without_local_pages_leaves_no_pages(self) -> None:
        """纯网址壳：注入后不应残留任何本地页面（否则导出物里挂着上辈子的文件）。"""
        self.inject(spec_with("index.html"), html_project([("index.html", "<html>a</html>")]))
        self.assertTrue(self.injected_files())

        url_spec = {"specVersion": 1, "topBar": {"actions": [{
            "id": "u", "title": "网页", "iconRef": None,
            "action": {"type": "url", "url": "https://example.com", "openInNewTab": False},
            "visible": True, "selected": False,
        }]}}
        self.assertEqual(0, self.inject(url_spec, None))
        self.assertEqual([], self.injected_files())

    def test_repeated_inject_is_stable(self) -> None:
        files = [("index.html", "<html>x</html>"), ("page/about.html", "<html>y</html>")]
        for _ in range(3):
            self.assertEqual(0, self.inject(spec_with("index.html", "page/about.html"), html_project(files)))
        self.assertEqual(["index.html", "page/about.html"], self.injected_files())


# ── ⑤ 路径聚合 / 跨语言一致性 ─────────────────────────────────────────────
class ContractConsistencyTest(unittest.TestCase):

    def test_local_page_paths_covers_all_four_regions(self) -> None:
        """漏一区 = 一类静默漏检，导出后就是「点了没反应」。"""
        self.assertEqual(
            ["top.html", "tabs.html", "bottom.html", "drawer.html"],
            inj.local_page_paths(spec_with("top.html", "tabs.html", "bottom.html", "drawer.html")),
        )

    def test_local_page_paths_is_deduped_and_ordered(self) -> None:
        spec = spec_with("a.html", "b.html", "a.html")
        self.assertEqual(["a.html", "b.html"], inj.local_page_paths(spec))

    def test_non_local_page_actions_are_not_collected(self) -> None:
        spec = spec_with("a.html")
        spec.setdefault("bottomBar", {}).setdefault("items", []).append({
            "id": "z", "title": "网页", "iconRef": None,
            "action": {"type": "url", "url": "https://example.com"},
            "visible": True, "selected": False,
        })
        self.assertEqual(["a.html"], inj.local_page_paths(spec))

    def test_asset_dir_matches_exported_shell(self) -> None:
        """脚本的目录名必须与导出壳 `InjectedLocalPages.LOCAL_PAGE_ASSET_DIR` 字面量一致。

        两边各改一半，本地页面在真机上就是 100% 打不开（且不会报错）。
        """
        self.assertTrue(TEMPLATE_KOTLIN.is_file(), f"缺导出壳阅读器源码：{TEMPLATE_KOTLIN}")
        src = TEMPLATE_KOTLIN.read_text(encoding="utf-8")
        self.assertIn(f'LOCAL_PAGE_ASSET_DIR: String = "{inj.LOCAL_PAGE_ASSET_DIR}"', src,
                      "脚本与导出壳的资产目录名已漂移")
        self.assertIn('ASSET_URL_PREFIX: String = "file:///android_asset/"', src,
                      "导出壳必须用 file:///android_asset/ 读注入页面")


# ── ⑥ APK 自检（构建后校验 facet）—— 用假 APK 替身，不真跑 gradle ──────────
class ApkSelfCheckTest(InjectPipelineTestCase):

    def setUp(self) -> None:
        super().setUp()
        self.build_cmds: list[list[str]] = []

        class FakeResult:
            returncode = 0

        def fake_run(cmd, *_args, **_kwargs):  # 替身：本用例不真跑 gradle（那是 E2E 的事）
            self.build_cmds.append(list(cmd))
            return FakeResult()

        class FakeSubprocess:
            run = staticmethod(fake_run)

        self._saved_subprocess = inj.subprocess
        inj.subprocess = FakeSubprocess()

    def tearDown(self) -> None:
        inj.subprocess = self._saved_subprocess
        super().tearDown()

    def fake_apk(self, names: list[str]) -> Path:
        apk = self.root / "template" / "build" / "outputs" / "apk" / "debug" / "template-debug.apk"
        apk.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(apk, "w") as z:
            for n in names:
                z.writestr(n, "x")
        return apk

    def test_missing_local_page_in_apk_fails_the_gate(self) -> None:
        """引用了本地页面却没打进 APK —— 必须硬失败（这就是真机上的「点了没反应」）。"""
        self.fake_apk(["res/raw/shell_spec.json"])
        rc = inj.run_build(["index.html"])
        self.assertEqual(2, rc, "本地页面未打进 APK 必须拦住")

    def test_all_local_pages_present_passes_the_gate(self) -> None:
        self.fake_apk(["res/raw/shell_spec.json", "assets/mma_pages/index.html"])
        self.assertEqual(0, inj.run_build(["index.html"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
