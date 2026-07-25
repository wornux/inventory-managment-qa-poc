# Focused test patterns

## Normalization and filter forwarding

Return different objects for each repository call and verify exact normalized arguments. Never assert an unstubbed empty list.

```java
when(repository.search("tools", false)).thenReturn(List.of(inactive));

assertThat(service.search(new Filter(" TOOLS ", false)))
        .containsExactly(inactive);
verify(repository).search("tools", false);
```

## Permission capability

```java
var create = Set.of(RESOURCE_CREATE, RESOURCE_ASSIGN);
var update = Set.of(RESOURCE_UPDATE, RESOURCE_ASSIGN);
when(authorization.canAll(create)).thenReturn(true);
when(authorization.canAll(update)).thenReturn(false);

assertThat(service.canCreate()).isTrue();
assertThat(service.canUpdate()).isFalse();
verify(authorization).canAll(create);
verify(authorization).canAll(update);
```

## Domain failure

```java
assertThatThrownBy(() -> service.update(id, staleRequest))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("updated by another user");
verify(repository, never()).save(any());
```

## Complete mapper contract

Build a source with distinct values and compare the complete response record or extract every destination field. Add a separate nullable-reference case.

## Same-rule input matrix

Use `@ParameterizedTest` for enum policy tables, blank/null normalization, sign rules, or equivalent invalid values. Do not parameterize unrelated exceptions merely to reduce line count.
