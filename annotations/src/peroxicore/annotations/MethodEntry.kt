package peroxicore.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class MethodEntry(
  /** 匹配方法名 方法名:参数1,参数2 */
  val entryMethod: String,
  /** 当前注解的方法参数名["参数1","参数2"] */
  val params: Array<String> = [],
  val context: Array<String> = [],
  /** 插入方式 */
  val insert: InsertPosition = InsertPosition.END,
)

enum class InsertPosition {
  HEAD,
  END,
  OVERRIDE,
  INJECT,
}
