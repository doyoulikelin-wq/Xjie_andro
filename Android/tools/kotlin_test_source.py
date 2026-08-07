"""Small fail-closed Kotlin source scanner used by exact test inventories.

The scanner is intentionally limited to package, class-body, and ``@Test fun``
ownership. It masks comments and literals, then matches braces, so a nested
helper declaration that has already ended cannot steal tests from its owner.
"""

from __future__ import annotations

import re
from dataclasses import dataclass


PACKAGE_PATTERN = re.compile(r"^\s*package\s+([A-Za-z_][\w.]*)", re.MULTILINE)
CLASS_PATTERN = re.compile(r"\bclass\s+([A-Za-z_][A-Za-z0-9_]*)")
TEST_PATTERN = re.compile(
    r"@Test(?:\s*\([^)]*\))?(?:(?:\s*@[^\n]+\n)*)\s*fun\s+"
    r"(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)\s*\(",
    re.MULTILINE,
)


class KotlinTestSourceError(RuntimeError):
    pass


@dataclass(frozen=True)
class KotlinTestDeclaration:
    owner: str
    method: str


def _mask_non_code(source: str) -> str:
    """Replace comments and literals with spaces while preserving offsets/newlines."""

    masked = list(source)
    index = 0
    state = "code"
    state_stack: list[str] = []
    interpolation_depths: list[int] = []
    block_depth = 0
    escaped = False
    length = len(source)

    def blank(position: int) -> None:
        if masked[position] not in "\r\n":
            masked[position] = " "

    while index < length:
        current = source[index]
        following = source[index + 1] if index + 1 < length else ""
        triple = source[index : index + 3]

        if state in {"code", "interpolation"}:
            if state == "interpolation":
                blank(index)
                if current == "{":
                    interpolation_depths[-1] += 1
                    index += 1
                    continue
                if current == "}":
                    interpolation_depths[-1] -= 1
                    index += 1
                    if interpolation_depths[-1] == 0:
                        interpolation_depths.pop()
                        state = state_stack.pop()
                    continue
            if current == "/" and following == "/":
                blank(index)
                blank(index + 1)
                index += 2
                state_stack.append(state)
                state = "line_comment"
                continue
            if current == "/" and following == "*":
                blank(index)
                blank(index + 1)
                index += 2
                state_stack.append(state)
                state = "block_comment"
                block_depth = 1
                continue
            if triple == '\"\"\"':
                for offset in range(3):
                    blank(index + offset)
                index += 3
                state_stack.append(state)
                state = "triple_string"
                continue
            if current == '\"':
                blank(index)
                index += 1
                state_stack.append(state)
                state = "string"
                escaped = False
                continue
            if current == "'":
                blank(index)
                index += 1
                state_stack.append(state)
                state = "character"
                escaped = False
                continue
            index += 1
            continue

        if state == "line_comment":
            blank(index)
            if current in "\r\n":
                state = state_stack.pop()
            index += 1
            continue

        if state == "block_comment":
            if current == "/" and following == "*":
                blank(index)
                blank(index + 1)
                block_depth += 1
                index += 2
                continue
            if current == "*" and following == "/":
                blank(index)
                blank(index + 1)
                block_depth -= 1
                index += 2
                if block_depth == 0:
                    state = state_stack.pop()
                continue
            blank(index)
            index += 1
            continue

        if state == "triple_string":
            if current == "$" and following == "{":
                blank(index)
                blank(index + 1)
                index += 2
                state_stack.append(state)
                interpolation_depths.append(1)
                state = "interpolation"
                continue
            if triple == '\"\"\"':
                for offset in range(3):
                    blank(index + offset)
                index += 3
                state = state_stack.pop()
                continue
            blank(index)
            index += 1
            continue

        blank(index)
        if escaped:
            escaped = False
        elif current == "\\":
            escaped = True
        elif state == "string" and current == "$" and following == "{":
            blank(index + 1)
            index += 2
            state_stack.append(state)
            interpolation_depths.append(1)
            state = "interpolation"
            continue
        elif state == "string" and current == '\"':
            state = state_stack.pop()
        elif state == "character" and current == "'":
            state = state_stack.pop()
        index += 1

    if state != "code" or state_stack or interpolation_depths:
        raise KotlinTestSourceError(f"unterminated Kotlin {state.replace('_', ' ')}")
    return "".join(masked)


def _class_body_open(masked: str, start: int) -> int | None:
    parentheses = 0
    brackets = 0
    index = start
    while index < len(masked):
        current = masked[index]
        if current == "(":
            parentheses += 1
        elif current == ")":
            parentheses = max(0, parentheses - 1)
        elif current == "[":
            brackets += 1
        elif current == "]":
            brackets = max(0, brackets - 1)
        elif parentheses == 0 and brackets == 0:
            if current == "{":
                return index
            if current == ";" or current == "=":
                return None
            remainder = masked[index:]
            if re.match(
                r"@Test\b|fun\b|(?:data\s+|sealed\s+|enum\s+|annotation\s+)?class\b",
                remainder,
            ):
                return None
        index += 1
    return None


def _matching_brace(masked: str, opening: int) -> int:
    depth = 0
    for index in range(opening, len(masked)):
        if masked[index] == "{":
            depth += 1
        elif masked[index] == "}":
            depth -= 1
            if depth == 0:
                return index
    raise KotlinTestSourceError("unmatched Kotlin class body brace")


def discover_kotlin_tests(source: str) -> tuple[str | None, list[KotlinTestDeclaration]]:
    masked = _mask_non_code(source)
    package_match = PACKAGE_PATTERN.search(masked)
    class_ranges: list[tuple[int, int, str]] = []
    for class_match in CLASS_PATTERN.finditer(masked):
        opening = _class_body_open(masked, class_match.end())
        if opening is not None:
            class_ranges.append(
                (opening, _matching_brace(masked, opening), class_match.group(1))
            )

    declarations: list[KotlinTestDeclaration] = []
    for test_match in TEST_PATTERN.finditer(masked):
        owners = [
            item for item in class_ranges if item[0] < test_match.start() < item[1]
        ]
        if not owners:
            raise KotlinTestSourceError(f"@Test has no owning class at offset {test_match.start()}")
        owner = max(owners, key=lambda item: item[0])[2]
        declarations.append(
            KotlinTestDeclaration(owner=owner, method=test_match.group(1).strip("`"))
        )
    return package_match.group(1) if package_match else None, declarations
