package com.printcalculator.controller;

import com.printcalculator.service.order.OrderControllerService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CadDownloadControllerTest {
    @Test void zipIsHandledAsAsyncStreamBySpringMvc() throws Exception {
        var service = mock(OrderControllerService.class);
        UUID id = UUID.randomUUID();
        byte[] zipBytes = {80, 75, 3, 4};
        StreamingResponseBody stream = output -> output.write(zipBytes);
        doReturn(ResponseEntity.ok().header("Content-Disposition", "attachment; filename=files.zip")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip")).body(stream))
                .when(service).downloadCadFiles(id);
        var mvc = MockMvcBuilders.standaloneSetup(new OrderController(service)).build();
        var result = mvc.perform(get("/api/orders/{id}/cad-files/download", id))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentType("application/zip")).andExpect(content().bytes(zipBytes));
    }
}
