package com.wornux.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditBehaviorTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void currentUsernameRejectsAbsentUnauthenticatedAnonymousAndBlankIdentities() {
        try (var security = mockStatic(SecurityContextHolder.class)) {
            security.when(SecurityContextHolder::getContext).thenReturn(null);
            assertThat(CurrentUserUtils.currentUsername()).isEqualTo(CurrentUserUtils.ANONYMOUS);
        }
        assertThat(CurrentUserUtils.currentUsername()).isEqualTo(CurrentUserUtils.ANONYMOUS);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("u", "p"));
        assertThat(CurrentUserUtils.currentUsername()).isEqualTo(CurrentUserUtils.ANONYMOUS);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anon", AuthorityUtils.createAuthorityList("A")));
        assertThat(CurrentUserUtils.currentUsername()).isEqualTo(CurrentUserUtils.ANONYMOUS);
        var blank = mock(Authentication.class);
        when(blank.isAuthenticated()).thenReturn(true);
        when(blank.getName()).thenReturn(" ");
        SecurityContextHolder.getContext().setAuthentication(blank);
        assertThat(CurrentUserUtils.currentUsername()).isEqualTo(CurrentUserUtils.ANONYMOUS);
        when(blank.getName()).thenReturn(null);
        assertThat(CurrentUserUtils.currentUsername()).isEqualTo(CurrentUserUtils.ANONYMOUS);
        SecurityContext nullContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(nullContext);
        assertThat(CurrentUserUtils.currentUsername()).isEqualTo(CurrentUserUtils.ANONYMOUS);
    }

    @Test void authenticatedNameFeedsAuditorAndRevision() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", "p", AuthorityUtils.NO_AUTHORITIES));
        assertThat(CurrentUserUtils.currentUsername()).isEqualTo("alice");
        assertThat(new AuditConfig().auditorAware().getCurrentAuditor()).contains("alice");
        var revision = new Revision();
        new RevisionListenerImpl().newRevision(revision);
        assertThat(revision.getModifierUser()).isEqualTo("alice");
        assertThat(revision.getIpAddress()).isEqualTo("0.0.0.0");
        assertThat(revision.toString()).contains("modifierUser='alice'", "ipAddress='0.0.0.0'");
    }

    @Test void auditableAliasesExposeDatesAndAuditProperties() {
        class Item extends Auditable {}
        var item = new Item();
        var created = Instant.EPOCH; var modified = Instant.MAX;
        item.setCreatedBy("a"); item.setLastModifiedBy("b"); item.setCreatedDate(created); item.setLastModifiedDate(modified);
        assertThat(item.getCreatedBy()).isEqualTo("a");
        assertThat(item.getLastModifiedBy()).isEqualTo("b");
        assertThat(item.getCreatedDate()).isEqualTo(created);
        assertThat(item.getLastModifiedDate()).isEqualTo(modified);
        assertThat(item.getCreatedAt()).isEqualTo(created);
        assertThat(item.getUpdatedAt()).isEqualTo(modified);
    }
}
