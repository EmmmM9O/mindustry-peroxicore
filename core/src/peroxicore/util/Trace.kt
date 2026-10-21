package peroxicore.util

fun getCurrentLocation(skip: Int = 0): String = StackTraceSourceLocator.locate(skip).toString()

fun getCurrentSourceLocation(skip: Int = 0): SourceLocation = StackTraceSourceLocator.locate(skip)
