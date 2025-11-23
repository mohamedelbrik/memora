
package com.memora.application.port.out;

import com.memora.domain.MemoryEvent;

public interface EventPublisher {
    void publish(MemoryEvent event);
}