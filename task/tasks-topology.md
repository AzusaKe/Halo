# Halo Mod — Task Topology & Execution Order

## Dependency Graph

```
Phase 0: project-init
  |
  v
Phase 1: data-model ─────────────────────┐
  |                                       │
  v                                       │
Phase 2: server-core ─────────┐           │
  |                           │           │
  v                           v           │
Phase 3: networking ─────> Phase 4: client-render
                              │
                              v
                        Phase 5: depth-glow
                              │
                              v
                        Phase 6: config-polish
```

## Verification Chain (parallel where possible)

```
verify-data ──> verify-server ──> verify-render ──> verify-integration
     ↑               ↑                ↑                  ↑
  Phase 1        Phase 2-3        Phase 4-5          Phase 6
```

## Topological Execution Order

| Order | Task ID | Task File | Depends On | Can Parallel With |
|-------|---------|-----------|------------|-------------------|
| 1 | `project-init` | `task-00-project-init.md` | — | — |
| 2 | `data-model` | `task-01-data-model.md` | `project-init` | — |
| 3 | `verify-data` | `task-verify-01-data.md` | `data-model` | `server-core` |
| 4 | `server-core` | `task-02-server-core.md` | `data-model` | `verify-data` |
| 5 | `networking` | `task-03-networking.md` | `server-core` | — |
| 6 | `verify-server` | `task-verify-02-server.md` | `server-core`, `networking` | `client-render` |
| 7 | `client-render` | `task-04-client-render.md` | `data-model`, `networking` | `verify-server` |
| 8 | `depth-glow` | `task-05-depth-glow.md` | `client-render` | — |
| 9 | `verify-render` | `task-verify-03-render.md` | `depth-glow` | `config-polish` |
| 10 | `config-polish` | `task-06-config-polish.md` | `depth-glow` | `verify-render` |
| 11 | `verify-integration` | `task-verify-04-integration.md` | `config-polish` | — |

## Execution Strategy

**Level 0** (run solo, blocks everything):
- `project-init`

**Level 1** (run after Level 0):
- `data-model`

**Level 2** (run after Level 1, can run in parallel):
- `server-core` || `verify-data`

**Level 3** (run after Level 2):
- `networking`

**Level 4** (run after Level 3, can run in parallel):
- `client-render` || `verify-server`

**Level 5** (run after Level 4):
- `depth-glow`

**Level 6** (run after Level 5, can run in parallel):
- `config-polish` || `verify-render`

**Level 7** (run after Level 6):
- `verify-integration`
