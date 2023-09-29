package io.kestra.plugin.weaviate;

import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
public class QueryTest {

    public static final String SCHEME = "http";
    public static final String HOST = "localhost:8080";
    private static final String QUERY = """
                       {
                          Get {
                            %s {
                                title
                            }
                          }
                        }
                       """;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    private URI putFile(URL resource, String path) throws Exception {
        return storageInterface.put(
            new URI(path),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );
    }

    @Test
    public void testQueryWithoutInternalStorage() throws Exception {
        RunContext runContext = runContextFactory.of();

        String className = "QueryTest_1";
        List<Map<String, Object>> parameters = List.of(Map.of("title", "test success"));

        BatchCreate.Output batchOutput = BatchCreate.builder()
            .scheme(SCHEME)
            .host(HOST)
            .className(className)
            .objects(parameters)
            .build()
            .run(runContext);

        assertThat(batchOutput.getCreatedCount(), is(1));

        Query.Output queryOutput = Query.builder()
            .scheme(SCHEME)
            .host(HOST)
            .query("""
                   {
                          Get {
                            %s (
                              limit: 50
                            ) {
                              title
                              _additional {
                                id
                              }
                            }
                          }
                        }
                   """.formatted(className))
            .fetchType(FetchType.FETCH_ONE)
            .build()
            .run(runContext);

        assertThat(queryOutput.getSize(), is(1));

        Map<String, Object> stringObjectMap = (Map<String, Object>) ((List<Object>) queryOutput.getRow().get(className)).get(0);
        String id = (String) ((Map<String, Object>) stringObjectMap.remove("_additional")).get("id");
        assertThat(parameters, Matchers.containsInAnyOrder(stringObjectMap));
    }

    @Test
    public void testQueryWithInternalStorage() throws Exception {
        RunContext runContext = runContextFactory.of();

        String className = "QueryTest_2";
        List<Map<String, Object>> parameters = List.of(Map.of("title", "test success"));

        BatchCreate.Output batchOutput = BatchCreate.builder()
            .scheme(SCHEME)
            .host(HOST)
            .className(className)
            .objects(parameters)
            .build()
            .run(runContext);

        assertThat(batchOutput.getCreatedCount(), is(1));

        Query.Output queryOutput = Query.builder()
            .scheme(SCHEME)
            .host(HOST)
            .query(QUERY.formatted(className))
            .fetchType(FetchType.STORE)
            .build()
            .run(runContext);

        assertThat(queryOutput.getSize(), is(1));

        assertThat(parameters, Matchers.containsInAnyOrder(((List<Object>) queryOutput.getRows().get(0).get(className)).get(0)));

        String outputFileContent = IOUtils.toString(storageInterface.get(queryOutput.getUri()), Charsets.UTF_8);
        Map rows = JacksonMapper.ofIon().readValue(outputFileContent, Map.class);
        assertThat(rows.get(className), is(queryOutput.getRows().get(0).get(className)));
    }
}
