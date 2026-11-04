package com.soham.crawler.search;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CranfieldEvaluationTest {
    @TempDir Path directory;

    @Test
    void readsJudgedQuestionsAndTreatsMinusOneAsStrongestRelevance() throws Exception {
        Files.writeString(directory.resolve("cran.all.1400"),
                ".I 1\n.T\nwing lift\n.W\nlift from a wing\n"
                + ".I 2\n.T\nthermal systems\n.W\nheat transfer in a wall\n");
        Files.writeString(directory.resolve("cran.qry"),
                ".I 001\n.W\nhow does a wing create lift\n"
                + ".I 004\n.W\nhow does heat move in a wall\n");
        Files.writeString(directory.resolve("cranqrel"), "1 1 -1\n1 2 4\n2 2 -1\n");
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            CranfieldEvaluation.main(new String[] {directory.toString()});
        } finally {
            System.setOut(original);
        }
        String report = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("documents=2 queries=2 judged_queries=2"));
        assertTrue(report.contains("variant=title_only queries=2 hit_at_10=0.5000"));
        assertTrue(report.contains("variant=title_and_abstract queries=2 hit_at_10=1.0000"));
    }
}
