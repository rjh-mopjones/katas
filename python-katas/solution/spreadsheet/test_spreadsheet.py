import pytest

from . import CycleError, Sheet


def test_literal_and_empty_cells():
    s = Sheet()
    s["A1"] = 5
    assert s["A1"] == 5.0
    assert s["Z9"] == 0.0  # empty cell reads as zero


def test_formula_reads_other_cells():
    s = Sheet()
    s["A1"] = 2
    s["A2"] = 3
    s["A3"] = "=A1+A2"
    assert s["A3"] == 5.0


def test_operator_precedence():
    s = Sheet()
    s["A1"] = 2
    s["A2"] = 3
    s["B1"] = "=A1+A2*4"  # 2 + 12
    assert s["B1"] == 14.0


def test_changing_a_cell_updates_dependents():
    s = Sheet()
    s["A1"] = 10
    s["A2"] = "=A1*2"
    assert s["A2"] == 20.0
    s["A1"] = 100  # dependents reflect the new value on next read
    assert s["A2"] == 200.0


def test_transitive_dependencies():
    s = Sheet()
    s["A1"] = 1
    s["A2"] = "=A1+1"
    s["A3"] = "=A2+1"
    assert s["A3"] == 3.0


def test_direct_cycle_is_rejected():
    s = Sheet()
    s["A1"] = "=A1+1"
    with pytest.raises(CycleError):
        _ = s["A1"]


def test_indirect_cycle_is_rejected():
    s = Sheet()
    s["A1"] = "=B1"
    s["B1"] = "=A1"
    with pytest.raises(CycleError):
        _ = s["A1"]


def test_division_and_subtraction():
    s = Sheet()
    s["A1"] = 10
    s["A2"] = "=A1/4-0.5"
    assert s["A2"] == 2.0


def test_bad_reference_rejected():
    s = Sheet()
    with pytest.raises(ValueError):
        s["a1"] = 5  # lower-case is not a valid ref


def test_contains():
    s = Sheet()
    s["A1"] = 1
    assert "A1" in s
    assert "A2" not in s
