package org.kansei.wirehood.config;

import org.kansei.wirehood.model.WirehoodUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import reactor.core.publisher.Mono;

/**
 * WirehoodUser has a client-assigned (not DB-generated) @Id, so it implements Persistable with isNew defaulting true
 * Without this callback, a row freshly loaded from the DB would still report isNew()=true, causing a later save() to wrongly INSERT instead of UPDATE
 */
@Configuration
public class R2dbcPersistableConfig {

    @Bean
    public AfterConvertCallback<WirehoodUser> wirehoodUserAfterConvertCallback() {
        return (entity, table) -> {
            entity.setNew(false);
            return Mono.just(entity);
        };
    }
}
