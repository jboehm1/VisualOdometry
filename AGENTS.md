# VECTOR VIO — instructions for repository agents

Before changing this repository, read `docs/DEVELOPMENT_COORDINATION.md` from the current integration branch `feature/slam-explorer`, then the documentation for your module. This applies to code, tests and build changes. User instructions take precedence.

## Starting or resuming work

- Refresh the remote refs without overwriting uncommitted work. If your branch predates this file, read the integration copy with `git show origin/feature/slam-explorer:docs/DEVELOPMENT_COORDINATION.md`.
- State the integration SHA you inspected, your working branch, your task scope and the shared files you expect to change. Summarize the coordination risks relevant to that scope; a bare claim that you read the document is insufficient.
- Use an isolated branch/worktree for parallel work. Inspect concurrent changes to MainActivity, AndroidManifest, menus, exports and workflows before modifying them.
- The coordination document is a dated snapshot, not a live lock or proof that a branch is still current. Recheck heads and CI at the exact SHAs before integration.

## Integration and evidence

- Preserve the other modules when resolving conflicts. Do not choose a whole-file ours/theirs resolution that silently removes another feature.
- Keep DATA actions distinct and verify each action after combining branches.
- Test the combined result. Separate green branch builds do not validate the merged application.
- Identify the exact engine in every benchmark. Python PDR baseline results do not validate the Java Android PDR engine. Fixture tests do not establish real-dataset or physical-device accuracy.
- Preserve the separation between pose sampling and VIEW presentation rate, raw data and derived estimates, measured GPS and candidate alignment, and ground truth and estimator inputs.
- At handoff, record the commit, tests actually run, remaining limits and shared-file changes in the relevant module/coordination documentation.
- Do not claim background coordination, access to other chats' unpushed files, or production qualification without evidence.
