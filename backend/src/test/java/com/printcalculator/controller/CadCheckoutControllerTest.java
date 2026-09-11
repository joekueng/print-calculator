package com.printcalculator.controller;

import com.printcalculator.service.quote.CadCheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CadCheckoutControllerTest {
    @Test void validatesQuantityAndColorBeforeCallingTheService() throws Exception {
        var service = mock(CadCheckoutService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new CadCheckoutController(service)).build();
        String path = "/api/quote-sessions/" + UUID.randomUUID() + "/cad-items/" + UUID.randomUUID();
        for (String json : new String[]{"{}", "{\"quantity\":0,\"filamentVariantId\":1}",
                "{\"quantity\":-2,\"filamentVariantId\":1}", "{\"quantity\":1}"}) {
            mvc.perform(patch(path).contentType("application/json").content(json)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        when(service.update(any(), any(), any())).thenReturn(Map.of("grandTotalChf", 42));
        mvc.perform(patch(path).contentType("application/json").content("{\"quantity\":3,\"filamentVariantId\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grandTotalChf").value(42));
    }
}
