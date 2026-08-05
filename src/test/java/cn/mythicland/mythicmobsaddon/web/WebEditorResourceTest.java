package cn.mythicland.mythicmobsaddon.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebEditorResourceTest {

    @Test
    void acceptsObjectAttributesInEditorContract() throws IOException {
        String app = resource("web/app.js");
        String index = resource("web/index.html");

        assertTrue(app.contains("JSON.stringify(config.Attributes ?? {}, null, 2)"));
        assertTrue(app.contains("const attributes = parseJsonField('attributes-json', {});"));
        assertTrue(app.contains("Attributes 必须是 JSON 对象或数组"));
        assertFalse(app.contains("Attributes 必须是 JSON 数组"));
        assertTrue(index.contains("id=\"attributes-json\""));
        assertTrue(index.contains("placeholder=\"{}\""));
    }

    private String resource(String name) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IOException("Missing test resource: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
