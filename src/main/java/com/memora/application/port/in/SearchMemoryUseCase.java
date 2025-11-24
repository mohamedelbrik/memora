package com.memora.application.port.in;

import com.memora.domain.Memory;
import java.util.List;

public interface SearchMemoryUseCase {
    List<Memory> search(String query);
}