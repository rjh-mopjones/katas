import pytest

from . import (
    Accept,
    Cancel,
    Fill,
    IllegalTransition,
    Order,
    OrderState,
    Overfill,
    Reject,
    apply,
)


def test_happy_path_new_to_filled_via_partial():
    o = Order(state=OrderState.NEW, total=10)
    o = apply(o, Accept())
    assert o.state is OrderState.ACCEPTED
    o = apply(o, Fill(4))
    assert o.state is OrderState.PARTIALLY_FILLED
    assert o.filled == 4
    o = apply(o, Fill(6))
    assert o.state is OrderState.FILLED
    assert o.filled == 10


def test_one_shot_exact_fill():
    o = Order(state=OrderState.ACCEPTED, total=10)
    o = apply(o, Fill(10))
    assert o.state is OrderState.FILLED
    assert o.filled == 10


def test_cancel_from_new():
    o = apply(Order(state=OrderState.NEW, total=5), Cancel())
    assert o.state is OrderState.CANCELLED


def test_cancel_from_accepted():
    o = apply(Order(state=OrderState.ACCEPTED, total=5), Cancel())
    assert o.state is OrderState.CANCELLED


def test_cancel_from_partially_filled():
    o = apply(Order(state=OrderState.PARTIALLY_FILLED, total=5, filled=2), Cancel())
    assert o.state is OrderState.CANCELLED


def test_reject_from_new():
    o = apply(Order(state=OrderState.NEW, total=5), Reject())
    assert o.state is OrderState.REJECTED


def test_overfill_from_accepted():
    with pytest.raises(Overfill):
        apply(Order(state=OrderState.ACCEPTED, total=10), Fill(11))


def test_overfill_from_partially_filled():
    with pytest.raises(Overfill):
        apply(Order(state=OrderState.PARTIALLY_FILLED, total=10, filled=7), Fill(4))


def test_new_plus_fill_is_illegal():
    with pytest.raises(IllegalTransition):
        apply(Order(state=OrderState.NEW, total=10), Fill(1))


def test_accepted_plus_accept_or_reject_is_illegal():
    accepted = Order(state=OrderState.ACCEPTED, total=10)
    with pytest.raises(IllegalTransition):
        apply(accepted, Accept())
    with pytest.raises(IllegalTransition):
        apply(accepted, Reject())


def test_partially_filled_plus_accept_or_reject_is_illegal():
    partial = Order(state=OrderState.PARTIALLY_FILLED, total=10, filled=3)
    with pytest.raises(IllegalTransition):
        apply(partial, Accept())
    with pytest.raises(IllegalTransition):
        apply(partial, Reject())


def test_every_terminal_state_rejects_every_event():
    terminals = [OrderState.FILLED, OrderState.CANCELLED, OrderState.REJECTED]
    events: list = [Accept(), Fill(1), Cancel(), Reject()]
    for state in terminals:
        order = Order(state=state, total=10, filled=10 if state is OrderState.FILLED else 0)
        for event in events:
            with pytest.raises(IllegalTransition):
                apply(order, event)


def test_apply_does_not_mutate_input():
    o = Order(state=OrderState.NEW, total=10)
    result = apply(o, Accept())
    assert result is not o
    assert o.state is OrderState.NEW  # original untouched
    assert result.state is OrderState.ACCEPTED
