package org.arcure.back.config.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@PreAuthorize("isMyPlayer(#gameId, #playerId)")
public @interface IsMyPlayer {
}
