package voldemar.dev.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AppControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testFeatureFlagLifecycle() throws Exception {
        mockMvc.perform(get("/message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("hello"));

        mockMvc.perform(get("/admin/feature").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello from new feature flag!"));

        mockMvc.perform(get("/admin/feature").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("hello"));
    }
}
