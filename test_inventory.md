# Watney4 Test Inventory

This file tracks what’s been tested so far during the alpha/beta phase. Updates will be added as we go.

## **Memory Systems**
- ✅ **Basic Recall**: Saved JSpring conference attendance and recalled it correctly.

## **Task Execution**
- ✅ **File Operations**: Read/write files, glob patterns, and basic shell commands (e.g., `ls`, `grep`).
- ✅ **Tool Delegation**: Used `opencode` for complex tasks (e.g., debugging, refactoring).

## **Error Handling & Debugging**
- ✅ **Misinterpretation Recovery**: Tested handling ambiguous requests and clarifying with follow-ups.

## **User Interaction**
- ✅ **Discord CLI**: Text-based commands (`/clear`, `/voice`, `/status`).
- ✅ **TTS Testing**: Sent short audio clips via `espeak-ng`.

## **Environment Awareness**
- ✅ **File System Access**: Confirmed read/write permissions in `/home/hawk0120/Vault` and `/home/hawk0120/dev/`.
- ✅ **OS/Toolchain**: Verified Java 21 (GraalVM) and Linux environment.

## **Next Steps** (Proposed)
1. Test **error injection** (e.g., intentional failures like wrong file paths) to see how I recover.
2. Test **cross-machine sync** (if needed) to ensure consistency between `bitnest3` and `bitnest5`.

*Last updated: [Insert timestamp here]*