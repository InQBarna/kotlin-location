/* 
 * Copyright 2014 InQBarna Kenkyuu Jo SL 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */ 

package com.inqbarna.libsamples

import androidx.recyclerview.widget.RecyclerView
import com.google.common.collect.Collections2
import com.google.common.truth.IterableSubject
import com.google.common.truth.Truth.assertThat
import com.inqbarna.adapters.BasicBindingAdapter
import com.inqbarna.adapters.BasicItemBinder
import com.inqbarna.adapters.TypeMarker
import io.reactivex.functions.Predicate
import io.reactivex.observers.TestObserver
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.util.concurrent.RoboExecutorService
import org.robolectric.shadows.ShadowLog
import timber.log.Timber
import java.util.concurrent.ExecutorService

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 23/02/2018
 */
@RunWith(RobolectricTestRunner::class)
class BindingAdapterTest {


    companion object {
        val INITIAL_DATA_SET: List<TestItems> = listOf(
                TestItems(0, "texto 0"),
                TestItems(1, "texto 1"),
                TestItems(2, "texto 2"),
                TestItems(3, "texto 3"),
                TestItems(4, "texto 4"),
                TestItems(5, "texto 5"),
                TestItems(6, "texto 6"),
                TestItems(7, "texto 7"),
                TestItems(8, "texto 8"),
                TestItems(9, "texto 9"),
                TestItems(10, "texto 10"),
                TestItems(11, "texto 11"),
                TestItems(12, "texto 12")
        )
    }

    lateinit var adapter: BasicBindingAdapter<TestItems>
    lateinit var observer: TestAdapterObserver
    lateinit var offThreadExecutor: ExecutorService

    @Before
    fun setupAdapter() {
        ShadowLog.stream = System.out
        observer = TestAdapterObserver()
        offThreadExecutor = RoboExecutorService()
        adapter = BasicBindingAdapter<TestItems>(BasicItemBinder(0), offThreadExecutor).also {
            it.setDiffCallback(DiffCallback)
            it.setItems(INITIAL_DATA_SET)
            it.registerAdapterDataObserver(observer)
        }
    }

    @After
    fun tearDown() {
        ShadowLog.reset()
        Timber.uprootAll()
        offThreadExecutor.shutdown()
    }

    private object DiffCallback : BasicBindingAdapter.DiffCallback<TestItems> {
        override fun areSameEntity(a: TestItems, b: TestItems): Boolean {
            return a.id == b.id
        }

        override fun areContentEquals(a: TestItems, b: TestItems): Boolean {
            return a.text == b.text
        }
    }

    @Test
    fun testSecondWeHave() {
        adapter.setItems(listOf(
                TestItems(1, "Second")
        ))

        val newItems = listOf(
                TestItems(0, "First"),
                TestItems(1, "Changed second"),
                TestItems(2, "Third"),
                TestItems(3, "Fourth")
        )

        val resultsObserver = TestObserver<List<TestItems>>()
        adapter.updateItems(newItems).subscribe(resultsObserver)

        resultsObserver.assertComplete()
        resultsObserver.assertValue(Predicate {
            assertThat(it).containsAllIn(newItems).inOrder()
            return@Predicate true
        })

    }

    @Test
    fun `original task is aborted when not subscribed`() {
        adapter.setItems(listOf(
                TestItems(1, "Second")
        ))

        val newItems = listOf(
                TestItems(0, "First"),
                TestItems(1, "Changed second"),
                TestItems(2, "Third"),
                TestItems(3, "Fourth")
        )

        val resultsObserver = TestObserver<List<TestItems>>().also {
            it.dispose()
        }
        adapter.updateItems(newItems).subscribeWith(resultsObserver)
        resultsObserver.assertEmpty()
    }

    @Test
    fun testAnotherCombination() {
        adapter.setItems(listOf(
                TestItems(0, "A"),
                TestItems(1, "B"),
                TestItems(2, "C"),
                TestItems(3, "D"),
                TestItems(4, "E")
        ))

        val newItems = listOf(
                TestItems(0, "A"),
                TestItems(2, "C"),
                TestItems(4, "E"),
                TestItems(3, "D")
        )

        val resultsObserver = TestObserver<List<TestItems>>()
        adapter.updateItems(newItems).subscribe(resultsObserver)

        resultsObserver.assertComplete()
        resultsObserver.assertValue(Predicate {
            assertThat(it).containsAllIn(newItems).inOrder()
            return@Predicate true
        })
    }

    @Test
    fun `test permutations of two lists, works properly`() {
        adapter.setItems(INITIAL_DATA_SET)

        val firstDataPermutations = Collections2.permutations(INITIAL_DATA_SET).take(80)

        val second_list = listOf(
                TestItems(0, "First"),
                TestItems(1, "Changed second"),
                TestItems(2, "Third"),
                TestItems(3, "Fourth")
        )

        val secondDataPermutations = Collections2.permutations(second_list).toList()

        val final_list = firstDataPermutations + secondDataPermutations
        Timber.d("Number of permutations to test: %d", final_list.size)
        final_list.forEachIndexed { index, items ->
            val resultsObserver = TestObserver<List<TestItems>>()
            adapter.updateItems(items).subscribe(resultsObserver)
            Timber.d("Processing iteration: %d", index)
            resultsObserver.assertComplete()
            resultsObserver.assertValue(Predicate {
                assertThat(it).containsAllIn(it).inOrder()
                return@Predicate true
            })
        }
    }

    @Test
    fun testMovesOnly() {
        val newItems = listOf(
                TestItems(1, "texto 1"),
                TestItems(8, "texto 8"),
                TestItems(2, "texto 2"),
                TestItems(3, "texto 3"),
                TestItems(4, "texto 4"),
                TestItems(5, "texto 5"),
                TestItems(6, "texto 6"),
                TestItems(7, "texto 7"),
                TestItems(9, "texto 9"),
                TestItems(0, "texto 0"),
                TestItems(10, "texto 10"),
                TestItems(12, "texto 12"),
                TestItems(11, "texto 11")
        )

        val resultsObserver = TestObserver<List<TestItems>>()
        adapter.updateItems(newItems).subscribe(resultsObserver)

        resultsObserver.assertComplete()
        resultsObserver.assertValue(Predicate {
            assertThat(it).containsAllIn(newItems).inOrder()
            return@Predicate true
        })
    }

    @Test
    fun testUpdateSequenceAddInMiddleAndChanges() {
        val newItems = mutableListOf(
                TestItems(80, "a"),
                TestItems(81, "b"),
                TestItems(82, "c"),
                TestItems(83, "d")
        )
        val newList = mutableListOf<TestItems>()
        INITIAL_DATA_SET.subList(0, 6).mapTo(newList) {
            if (it.id == 3) TestItems(3, "cambiado 3") else it
        }

        newList.addAll(newItems)

        INITIAL_DATA_SET.subList(6, 13).mapTo(newList) {
            when (it.id) {
                in 8..9 -> TestItems(it.id, "changed ${it.id}")
                else -> it
            }
        }

        val resultsObserver = TestObserver<List<TestItems>>()
        adapter.updateItems(newList).subscribe(resultsObserver)

        resultsObserver.assertComplete()

        resultsObserver.assertValue(Predicate {
            assertThat(it).containsAllIn(newList).inOrder()
            return@Predicate true
        })

        observer.assertThat().containsExactly(Event(ObserverEventKind.ADD, 6..9), Event(ObserverEventKind.CHANGE, 3..3), Event(ObserverEventKind.CHANGE, 8..8), Event(ObserverEventKind.CHANGE, 9..9))
    }

    @Test
    fun anotherConflictingCase() {
        adapter.setItems(listOf(
                TestItems(0, "A"),
                TestItems(1, "B")
        ))

        val newItems = listOf(
                TestItems(1, "B"),
                TestItems(0, "A"),
                TestItems(4, "E")
        )

        val resultsObserver = TestObserver<List<TestItems>>()
        adapter.updateItems(newItems).subscribe(resultsObserver)

        resultsObserver.assertComplete()
        resultsObserver.assertValue(Predicate {
            assertThat(it).containsAllIn(newItems).inOrder()
            return@Predicate true
        })
    }

    @Test
    fun testUpdateSequenceAdd4Before() {
        val newList = mutableListOf(
                TestItems(80, "a"),
                TestItems(81, "b"),
                TestItems(82, "c"),
                TestItems(83, "d")
        ).also { it.addAll(INITIAL_DATA_SET) }

        val resultsObserver = TestObserver<List<TestItems>>()
        adapter.updateItems(newList).subscribe(resultsObserver)

//        resultsObserver.awaitTerminalEvent(5, TimeUnit.SECONDS)
        resultsObserver.assertComplete()

        resultsObserver.assertValue(Predicate {
            assertThat(it).containsAllIn(newList).inOrder()
            return@Predicate true
        })

        observer.assertThat().containsExactly(Event(ObserverEventKind.ADD, 0..3))
    }



    data class TestItems(val id: Int, val text: String) : TypeMarker {
        override fun getItemType(): Int = 0
    }
}

class TestAdapterObserver : RecyclerView.AdapterDataObserver() {
    private val events = mutableListOf<Event>()

    override fun onChanged() {
        events.add(Event(ObserverEventKind.CHANGE, IntRange.EMPTY))
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
        events.add(Event(ObserverEventKind.REMOVE, positionStart until (positionStart + itemCount)))
    }

    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
        events.add(Event(ObserverEventKind.MOVE, fromPosition until (fromPosition + itemCount), toPosition))
    }

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        events.add(Event(ObserverEventKind.ADD, positionStart until (positionStart + itemCount)))
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
        events.add(Event(ObserverEventKind.CHANGE, positionStart until (positionStart + itemCount)))
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
        events.add(Event(ObserverEventKind.CHANGE, positionStart until (positionStart + itemCount)))
    }

    fun assertThat(): IterableSubject {
        return assertThat(events)
    }

}


enum class ObserverEventKind { ADD, REMOVE, MOVE, CHANGE }
data class Event(val kind: ObserverEventKind, val range: IntRange, val moveDestination: Int = -1)

private inline fun <reified T> Array<T>.shuffle(): Array<T> = toMutableList().shuffled().toTypedArray()
