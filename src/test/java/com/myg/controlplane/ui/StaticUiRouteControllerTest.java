package com.myg.controlplane.ui;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:testdb-ui-routes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "chaos.auth.mode=dev"
})
class StaticUiRouteControllerTest {

    private static final String DEV_USER_HEADER = "X-Chaos-Dev-User";
    private static final String DEV_ROLES_HEADER = "X-Chaos-Dev-Roles";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesPrettyFrontendRoutesForViewerTraffic() throws Exception {
        mockMvc.perform(asViewer(get("/experiments/")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/experiments/index.html"));

        mockMvc.perform(asViewer(get("/live-runs/").queryParam("run", "run-stg-401")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/live-runs/index.html"));

        mockMvc.perform(asViewer(get("/results/").queryParam("result", "result-stg-392")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/results/index.html"));

        mockMvc.perform(asViewer(get("/history/")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/history/index.html"));

        mockMvc.perform(asViewer(get("/fleet/").queryParam("agent", "agent-stg-07")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/fleet/index.html"));
    }

    private static MockHttpServletRequestBuilder asViewer(MockHttpServletRequestBuilder builder) {
        return builder.header(DEV_USER_HEADER, "viewer-demo")
                .header(DEV_ROLES_HEADER, "VIEWER");
    }
}
