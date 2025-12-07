package com.memora.adapter.in.web;

import com.memora.application.port.in.SearchMemoryUseCase;
import com.memora.domain.Memory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/memories")
public class MemorySearchController {

    private final SearchMemoryUseCase searchMemoryUseCase;

    public MemorySearchController(SearchMemoryUseCase searchMemoryUseCase) {
        this.searchMemoryUseCase = searchMemoryUseCase;
    }

//    @GetMapping("/search")
//    public List<Memory> search(@RequestParam("q") String query) {
//        return searchMemoryUseCase.search(query);
//    }
}