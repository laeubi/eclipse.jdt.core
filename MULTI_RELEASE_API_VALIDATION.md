# Multi-Release API Contract Validation

## Overview

This document describes the implementation of API contract validation for Multi-Release JARs in Eclipse JDT Core, addressing issue #4274.

## Problem Statement

The contract for a Multi-Release type is that its public API must remain consistent across all versions. Consider the following violation:

**Base Version (default)**:
```java
public class MRType {
    public String sayHello() {
        return "Hello";
    }
}
```

**Version 9**:
```java
public class MRType {
    public String sayHello(Locale locale) {  // Changed signature!
        return "Hello";
    }
}
```

This violates the API contract because code compiled against the base version calling `MRType.sayHello()` will fail at runtime on Java 9+ with a `NoSuchMethodError`.

## Solution

The implementation adds validation during compilation to detect and report such API contract violations.

### Implementation Components

#### 1. MultiReleaseApiValidator

A new class `org.eclipse.jdt.internal.core.builder.MultiReleaseApiValidator` that:

- **Collects Multi-Release Types**: Scans output folders for types with multiple versions
- **Extracts API Signatures**: Reads compiled .class files to extract public methods and fields
- **Validates Compatibility**: Compares later versions against the base version
- **Reports Violations**: Creates error markers on source files when API contracts are broken

#### 2. Integration Points

The validator is integrated into:

- **BatchImageBuilder**: Runs after all source files are compiled
- **IncrementalImageBuilder**: Runs after the incremental build loop completes

### Validation Rules

The validator enforces the following rules:

1. **Public Methods**: All public methods in the base version must exist in later versions with the exact same signature
   - Method name must match
   - Method descriptor (parameters and return type) must match
   
2. **Public Fields**: All public fields in the base version must exist in later versions
   - Field name must match
   - Field type must match

3. **Additions Allowed**: Later versions MAY add new public methods and fields

### How It Works

```
┌─────────────────────────────────────────────────────┐
│  Compilation Complete                                │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────┐
│  Collect Multi-Release Types                        │
│  - Iterate sourceLocations                          │
│  - Group types by name across versions              │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────┐
│  For Each Type with Multiple Versions               │
│  1. Read base version .class file                   │
│  2. Extract public API (methods + fields)           │
│  3. For each later version:                         │
│     - Read .class file                              │
│     - Extract public API                            │
│     - Compare against base                          │
│     - Report missing members                        │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────┐
│  Error Reporting                                     │
│  - Find source file for the versioned class         │
│  - Create error marker with descriptive message     │
└─────────────────────────────────────────────────────┘
```

### Error Messages

The validator produces clear, actionable error messages:

```
Multi-Release type 'p.MRType': public method 'sayHello' from base version is missing in version 9
```

```
Multi-Release type 'p.MRType': public field 'message' from base version is missing in version 11
```

## Testing

Comprehensive tests are provided in `MultiReleaseTests.java`:

### Test: Removed Method
```java
public void testMultiReleaseApiContractViolation_RemovedMethod()
```
Validates that removing a public method from a later version produces an error.

### Test: Changed Method Signature
```java
public void testMultiReleaseApiContractViolation_ChangedMethodSignature()
```
Validates that changing a method signature (e.g., adding parameters) is detected as removing the original method.

### Test: Added Method (Valid)
```java
public void testMultiReleaseApiContractValid_AddedMethod()
```
Validates that adding new methods to later versions is allowed.

### Test: Removed Field
```java
public void testMultiReleaseApiContractViolation_RemovedField()
```
Validates that removing a public field from a later version produces an error.

### Test: Multiple Versions
```java
public void testMultiReleaseApiContractValid_MultipleVersions()
```
Validates that the contract is enforced across multiple release versions (e.g., 9, 11, 17).

## Usage

The validation is **automatic** and runs as part of the normal build process. No special configuration is required.

When you compile a Multi-Release project:
1. Define a base source folder (e.g., `src/`)
2. Define versioned source folders with the `RELEASE` classpath attribute (e.g., `src9/` with release=9)
3. Compile the project
4. The validator automatically checks API compatibility

Example project structure:
```
project/
  src/                    # Base version (default)
    p/MRType.java
  src9/                   # Java 9 version
    p/MRType.java
  bin/                    # Output
    p/MRType.class        # Base version
    META-INF/versions/9/
      p/MRType.class      # Java 9 version
```

## Implementation Details

### Key Classes

- **MultiReleaseApiValidator**: Main validation logic
- **ClasspathMultiDirectory**: Source location with release attribute
- **ClassFileReader**: Reads compiled .class files to extract API

### Algorithm Complexity

- **Time**: O(T × V × M) where:
  - T = number of types
  - V = number of versions per type (typically 2-3)
  - M = average number of public members per type
  
- **Space**: O(T × V × M) for storing API signatures

### Performance Considerations

- Validation only runs after compilation completes
- Only processes types that have multiple versions
- Uses ClassFileReader for efficient .class file parsing
- Errors are collected and reported in batch

## Future Enhancements

Possible improvements for future releases:

1. **Return Type Checking**: Validate that method return types remain compatible (covariant returns allowed)
2. **Exception Checking**: Validate that exception declarations don't violate Liskov Substitution Principle
3. **Visibility Changes**: Detect when members become more restrictive
4. **Generic Signatures**: Validate generic type parameters remain compatible
5. **Annotation Consistency**: Check that important annotations remain consistent
6. **Performance**: Cache API signatures to avoid re-reading unchanged .class files

## References

- [JEP 238: Multi-Release JAR Files](https://openjdk.java.net/jeps/238)
- [Issue #4274](https://github.com/eclipse-jdt/eclipse.jdt.core/issues/4274)
- [Java Language Specification - Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html)
