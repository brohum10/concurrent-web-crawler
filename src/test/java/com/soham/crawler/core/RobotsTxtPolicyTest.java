package com.soham.crawler.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RobotsTxtPolicyTest {
    @Test
    void parsesWildcardDisallowRulesAndIgnoresOtherAgents() {
        String robots = """
                User-agent: ExampleBot
                Disallow: /bot-only

                User-agent: *
                Disallow: /private
                Disallow: /admin # inline comment
                """;

        assertEquals(List.of("/private", "/admin"), RobotsTxtPolicy.parseWildcardRules(robots));
    }
}
