package g.nsu.ru.server.controller;

import g.nsu.ru.server.model.operations.Entry;
import g.nsu.ru.server.model.operations.Operation;
import g.nsu.ru.server.services.OperationsLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping(value = "/log",produces = {MediaType.APPLICATION_JSON_VALUE})
@Api(tags="Log")
@RequiredArgsConstructor
class OperationsLogController {

    private static final String DONE = "{\"result\": \"DONE\"}";
    private final OperationsLogService operationsLogService;


    @GetMapping
    public List<Operation> all(){
        return operationsLogService.all();
    }



    @PostMapping
    @ApiOperation(value = "Insert")
    public String insert(@RequestBody Entry entry,
                                         BindingResult bindingResult) throws BindException {
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
        operationsLogService.insert(entry);
        return DONE;
    }


    @PutMapping("/{key}")
    public String insert(@PathVariable Long key,@RequestBody String val)  {
        operationsLogService.update(key, val);
        return  DONE;
    }

    @DeleteMapping("/{key}")
    public String delete(@PathVariable Long key)  {
        operationsLogService.delete(key);
        return  DONE;
    }


}
