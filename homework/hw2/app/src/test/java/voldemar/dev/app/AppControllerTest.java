package voldemar.dev.app;

import org.springframework.web.bind.annotation.RestController;

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
