# Watney4 Test Inventory

This file tracks what’s been tested so far during the alpha/beta phase. Updates will be added as we go.

## **Memory Systems**
- ✅ **Basic Recall**: Saved JSpring conference attendance and recalled it correctly.
- ❌ **Contextual Memory**: *Not yet tested*—ability to pull details from specific events (e.g., specific talks, attendees, or takeaways).

## **Task Execution**
- ✅ **File Operations**: Read/write files, glob patterns, and basic shell commands (e.g., `ls`, `grep`).
- ✅ **Tool Delegation**: Used `opencode` for complex tasks (e.g., debugging, refactoring).
- ❌ **Multi-Step Workflows**: *Not yet tested*—chaining tools (e.g., search → read → write) without manual intervention.

## **Error Handling & Debugging**
- ✅ **Misinterpretation Recovery**: Tested handling ambiguous requests and clarifying with follow-ups.
- ❌ **Edge Cases**: *Not yet tested*—intentional failures (e.g., wrong file paths, malformed inputs) and recovery.

## **User Interaction**
- ✅ **Discord CLI**: Text-based commands (`/clear`, `/voice`, `/status`).
- ✅ **TTS Testing**: Sent short audio clips via `espeak-ng`.
- ❌ **Voice/Real-Time**: *Not applicable*—no voice channel or real-time voice support.

## **Environment Awareness**
- ✅ **File System Access**: Confirmed read/write permissions in `/home/hawk0120/Vault` and `/home/hawk0120/dev/`.
- ✅ **OS/Toolchain**: Verified Java 21 (GraalVM) and Linux environment.
- ❌ **Cross-Machine Sync**: *Not tested*—behavior on `bitnest5` vs. `bitnest3` consistency.

## **Next Steps** (Proposed)
1. Test **contextual memory** (e.g., recalling JSpring details like talks or notes).
2. Try a **multi-step task** (e.g., find a file → extract data → summarize it).
3. Intentional **error injection** (e.g., wrong file path) to test recovery.

*Last updated: [Insert timestamp here]*