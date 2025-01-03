package g.nsu.ru.server.controller;

import g.nsu.ru.server.model.operations.Entry;
import g.nsu.ru.server.model.operations.Operation;
import g.nsu.ru.server.services.OperationsLogService;
import g.nsu.ru.server.services.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@RestController
@RequestMapping(value = "/raft", produces = {MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
class ClientController {

    private static final String DONE = "{\"result\": \"DONE\"}";
    private final OperationsLogService operationsLogService;

    private final StorageService storageService;

    @GetMapping("/getall")
    public List<Entry> all() {
        return storageService.all();
    }

    @GetMapping("/get/{key}")
    public String get(@PathVariable String key) {
        return String.format("{\"val\":\"%s\"}", storageService.get(key).toString());
    }


    @PostMapping("/put")
    public String update(@RequestBody Entry entry) {
        operationsLogService.insert(entry);
        return DONE;
    }

    @PostMapping("/compareAndSwap")
    public Boolean compareAndSwap(@RequestBody CompareAndSwapRequest request) {
        return operationsLogService.compareAndSwap(request.getKey(), request.getExpectedValue(), request.getNewValue());
    }


    @PutMapping("update/{key}")
    public String update(@PathVariable String key, @RequestBody Object val) {
        operationsLogService.update(key, val);
        return DONE;
    }

    @PostMapping("delete/{key}")
    public String delete(@PathVariable String key) {
        operationsLogService.delete(key);
        return DONE;
    }

    @GetMapping("/logs/get/all")
    public List<Operation> getAllLogs() {
        return operationsLogService.all();
    }
}
