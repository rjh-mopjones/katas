from . import Channel, Event, NotificationService


class RecordingChannel:
    """A plain channel — NOT a subclass of Channel. Structural typing lets it qualify."""

    def __init__(self) -> None:
        self.received: list[Event] = []

    def send(self, event: Event) -> None:
        self.received.append(event)


def test_plain_class_is_structurally_a_channel():
    ch = RecordingChannel()
    assert isinstance(ch, Channel)  # @runtime_checkable: no inheritance needed


def test_subscribe_and_publish_delivers():
    service = NotificationService()
    ch = RecordingChannel()
    service.subscribe("orders", ch)
    event = Event("orders", {"id": 1})
    service.publish(event)
    assert ch.received == [event]


def test_multiple_channels_all_receive_in_order():
    service = NotificationService()
    first, second = RecordingChannel(), RecordingChannel()
    service.subscribe("orders", first)
    service.subscribe("orders", second)
    event = Event("orders")
    service.publish(event)
    assert first.received == [event]
    assert second.received == [event]


def test_channel_only_gets_its_own_topic():
    service = NotificationService()
    ch = RecordingChannel()
    service.subscribe("orders", ch)
    service.publish(Event("rides"))  # different topic
    assert ch.received == []


def test_predicate_filters_events():
    service = NotificationService()
    ch = RecordingChannel()
    service.subscribe("orders", ch, predicate=lambda e: e.payload.get("total", 0) > 100)
    big = Event("orders", {"total": 250})
    small = Event("orders", {"total": 5})
    service.publish(big)
    service.publish(small)
    assert ch.received == [big]  # only the matching event is delivered


def test_publish_to_empty_topic_is_noop():
    service = NotificationService()
    service.publish(Event("nobody-listening"))  # must not raise


def test_subscriber_count():
    service = NotificationService()
    assert service.subscriber_count("orders") == 0
    service.subscribe("orders", RecordingChannel())
    service.subscribe("orders", RecordingChannel())
    assert service.subscriber_count("orders") == 2
    assert service.subscriber_count("rides") == 0
