package com.marcoslorcar.clementime.ui.screens.schedule

import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.PeriodConfig
import com.marcoslorcar.clementime.data.PeriodType
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.testing.FakeScheduleDao
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun scheduleStateCombinesDisplaySettingsAndFiltersTheEffectivePeriod() = runTest {
        val subject = Subject(
            id = 1, code = "A", name = "Algebra", color = 0,
            isActive = true, semester = 3, selectedLabGroup = "A"
        )
        val theory = ClassSlot(
            id = 1, subjectId = 1, dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0)
        )
        val selectedLab = theory.copy(id = 2, entryType = EntryType.LAB, labGroupName = "A")
        val otherLab = selectedLab.copy(id = 3, labGroupName = "B")
        val otherPeriod = subject.copy(id = 2, semester = 1)
        val inactive = subject.copy(id = 3, isActive = false)
        val dao = FakeScheduleDao(
            listOf(
                SubjectWithSlots(subject, listOf(theory, selectedLab, otherLab)),
                SubjectWithSlots(otherPeriod, emptyList()),
                SubjectWithSlots(inactive, emptyList())
            )
        )
        val settings = ScheduleSettingsFake()
        val viewModel = ScheduleViewModel(dao, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.selectedSemester)
        assertEquals(settings.periodConfigFlow.value, state.periodConfig)
        assertEquals(listOf(SubjectWithSlots(subject, listOf(theory, selectedLab))), state.subjectsWithSlots)
        assertTrue(state.hasOverlaps)
        assertTrue(state.hasAnySubjects)
        assertTrue(state.showAutoChangeTooltip)
        assertTrue(state.scrollableTabs)
        assertFalse(state.showNowLine)
        assertEquals("obvious", state.nowLineStyle)
        assertTrue(state.highContrast)
        assertFalse(state.onboardingTooltipsEnabled)
        assertTrue(state.hasSeenOptimizerTooltip)
        assertEquals(LocalTime.of(7, 15), state.dayStartTime)
        assertEquals(LocalTime.of(20, 45), state.dayEndTime)

        viewModel.changeTab(ScheduleTab.MONDAY)
        viewModel.toggleSemesterSwitcher()
        viewModel.changeSemester(1)
        advanceUntilIdle()
        val updated = viewModel.uiState.value
        assertEquals(ScheduleTab.MONDAY, updated.selectedTab)
        assertEquals(1, updated.selectedSemester)
        assertEquals(listOf(SubjectWithSlots(otherPeriod, emptyList())), updated.subjectsWithSlots)
        assertTrue(updated.isSemesterSwitcherVisible)
        assertFalse(updated.showAutoChangeTooltip)
        assertFalse(updated.hasOverlaps)
    }
}

private class ScheduleSettingsFake : SettingsRepository(context = null) {
    override val currentSemesterFlow = MutableStateFlow(8)
    override val periodConfigFlow = MutableStateFlow(PeriodConfig.normalized(PeriodType.TRIMESTERS, 3))
    override val hasManuallyChangedSemesterFlow = MutableStateFlow(false)
    override val wasSemesterAutoChangedFlow = MutableStateFlow(true)
    override val scrollableTabsFlow = MutableStateFlow(true)
    override val showNowLineFlow = MutableStateFlow(false)
    override val nowLineStyleFlow = MutableStateFlow("obvious")
    override val highContrastFlow = MutableStateFlow(true)
    override val onboardingTooltipsEnabledFlow = MutableStateFlow(false)
    override val hasSeenOptimizerTooltipFlow = MutableStateFlow(true)
    override val dayStartHourFlow = MutableStateFlow(7)
    override val dayStartMinuteFlow = MutableStateFlow(15)
    override val dayEndHourFlow = MutableStateFlow(20)
    override val dayEndMinuteFlow = MutableStateFlow(45)

    override suspend fun setCurrentSemester(semester: Int) {
        currentSemesterFlow.value = semester
    }

    override suspend fun setHasManuallyChangedSemester(hasChanged: Boolean) {
        hasManuallyChangedSemesterFlow.value = hasChanged
    }

    override suspend fun setWasSemesterAutoChanged(autoChanged: Boolean) {
        wasSemesterAutoChangedFlow.value = autoChanged
    }
}
