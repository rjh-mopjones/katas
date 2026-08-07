package org.kata.pipeline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {

    @Test
    void addAll_accepts_a_covariant_producer_collection() {
        // Pipeline<Number> loaded from a List<Integer> — only legal because addAll takes
        // Collection<? extends T>.
        Pipeline<Number> pipeline = Pipeline.<Number>create().addAll(List.of(1, 2, 3));

        assertEquals(List.of(1, 2, 3), pipeline.toList());
    }

    @Test
    void addAll_returns_this_for_chaining() {
        Pipeline<Number> pipeline = Pipeline.create();
        Pipeline<Number> same = pipeline.addAll(List.of(1, 2));

        assertSame(pipeline, same);
        assertEquals(List.of(1, 2), same.toList());
    }

    @Test
    void map_changes_the_element_type() {
        Pipeline<Integer> ints = Pipeline.<Integer>create().addAll(List.of(1, 2, 3));

        Pipeline<String> strings = ints.map(i -> "v" + i);

        assertEquals(List.of("v1", "v2", "v3"), strings.toList());
    }

    @Test
    void map_leaves_the_source_pipeline_untouched() {
        Pipeline<Integer> ints = Pipeline.<Integer>create().addAll(List.of(1, 2, 3));

        ints.map(i -> i * 10);

        assertEquals(List.of(1, 2, 3), ints.toList());
    }

    @Test
    void map_chains_across_multiple_type_changes() {
        Pipeline<Integer> ints = Pipeline.<Integer>create().addAll(List.of(1, 2, 3));

        Pipeline<Double> doubled = ints
                .map(i -> "v" + i)
                .map(String::length)
                .map(len -> len * 1.5);

        assertEquals(List.of(3.0, 3.0, 3.0), doubled.toList());
    }

    @Test
    void drainTo_accepts_a_contravariant_sink_collection() {
        // Pipeline<Number> draining into a List<Object> — only legal because drainTo takes
        // Collection<? super T>.
        Pipeline<Number> pipeline = Pipeline.<Number>create().addAll(List.<Number>of(1, 2.5, 3));
        List<Object> sink = new ArrayList<>();

        pipeline.drainTo(sink);

        assertEquals(List.<Number>of(1, 2.5, 3), sink);
    }

    @Test
    void drainTo_empties_the_pipeline() {
        Pipeline<Number> pipeline = Pipeline.<Number>create().addAll(List.of(1, 2, 3));
        List<Object> sink = new ArrayList<>();

        pipeline.drainTo(sink);

        assertTrue(pipeline.toList().isEmpty());
    }

    @Test
    void drainTo_appends_to_an_already_populated_sink() {
        Pipeline<Number> pipeline = Pipeline.<Number>create().addAll(List.of(2, 3));
        List<Object> sink = new ArrayList<>(List.of(1));

        pipeline.drainTo(sink);

        assertEquals(List.of(1, 2, 3), sink);
    }

    @Test
    void toList_returns_a_defensive_copy() {
        Pipeline<Integer> pipeline = Pipeline.<Integer>create().addAll(List.of(1, 2));

        List<Integer> snapshot = pipeline.toList();
        snapshot.add(99);

        assertEquals(List.of(1, 2), pipeline.toList());
    }

    @Test
    void static_copy_appends_a_covariant_source_into_a_contravariant_destination() {
        List<Number> dst = new ArrayList<>(List.of(0));
        List<Integer> src = List.of(1, 2, 3);

        Pipeline.copy(dst, src);

        assertEquals(List.of(0, 1, 2, 3), dst);
    }

    @Test
    void empty_pipeline_maps_and_drains_to_empty() {
        Pipeline<Integer> empty = Pipeline.create();

        assertTrue(empty.map(i -> i * 2).toList().isEmpty());

        List<Object> sink = new ArrayList<>();
        empty.drainTo(sink);
        assertTrue(sink.isEmpty());
    }
}
