package g.nsu.ru.server.controller;

import g.nsu.ru.server.model.operations.Entry;
import g.nsu.ru.server.model.operations.Operation;
import g.nsu.ru.server.services.OperationsLogService;
import g.nsu.ru.server.services.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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
    public String get(@PathVariable Long key) {
        return String.format("{\"val\":\"%s\"}", storageService.get(key));
    }


    @PostMapping("/kill-leader")
    public String killLeader() {
        operationsLogService.deactivateLeader();
        return DONE;
    }

    @PostMapping("/put")
    public String update(@RequestBody Entry entry) {
        operationsLogService.insert(entry);
        return DONE;
    }


    @PutMapping("update/{key}")
    public String update(@PathVariable Long key, @RequestBody String val) {
        operationsLogService.update(key, val);
        return DONE;
    }

    @PostMapping("delete/{key}")
    public String delete(@PathVariable Long key) {
        operationsLogService.delete(key);
        return DONE;
    }

    @GetMapping("/logs/get/all")
    public List<Operation> getAllLogs() {
        return operationsLogService.all();
    }
}
