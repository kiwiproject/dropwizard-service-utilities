package org.kiwiproject.dropwizard.util.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

@DisplayName("ServerLoadTask")
class ServerLoadTaskTest {

    @Test
    void testExecute() {
        var stringWriter = new StringWriter();
        var writer = new PrintWriter(stringWriter);

        var task = new ServerLoadTask();
        task.execute(Map.of(), writer);
        assertThat(stringWriter.toString().trim())
                .isNotNull()
                .matches("^\\d+.\\d+[,]? \\d+.\\d+[,]? \\d+.\\d+$");
    }
}
