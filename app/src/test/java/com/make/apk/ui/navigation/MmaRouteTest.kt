package com.make.apk.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 路由契约单测。
 *
 * 路由字符串是"跨文件契约"——导航图、结果回传、深链都依赖它，
 * 一旦改动就会静默断链，故用单测钉死。
 */
class MmaRouteTest {

    @Test
    fun addRoute_isStable() {
        assertEquals("add", MmaRoute.Add.route)
    }

    @Test
    fun projectRoute_createsPathWithId() {
        assertEquals("project/42", MmaRoute.Project.create(42L))
    }

    @Test
    fun projectRoute_templateContainsProjectIdArgument() {
        assertEquals("project/{projectId}", MmaRoute.Project.route)
    }

    @Test
    fun argumentKeys_areStable() {
        assertEquals("projectId", MmaRoute.ARG_PROJECT_ID)
    }

    @Test
    fun resultKeys_areStable() {
        assertEquals("result_created_project_id", MmaRoute.RESULT_CREATED_PROJECT_ID)
        assertEquals("result_updated_project_id", MmaRoute.RESULT_UPDATED_PROJECT_ID)
    }
}
