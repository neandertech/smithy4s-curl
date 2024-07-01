package smithy4s_curl

import scalanative.unsafe.*

private[smithy4s_curl] type Captured[D] = D
private[smithy4s_curl] object Captured:
  def unsafe[D <: AnyRef: Tag](value: D): (Ptr[Captured[D]], () => Unit) = {
    import scalanative.runtime.*

    val rawptr = libc.malloc(sizeof[Captured[D]])
    val mem = fromRawPtr[Captured[D]](rawptr)
    val deallocate: () => Unit =
      () =>
        GCRoots.removeRoot(value.asInstanceOf[Object])
        libc.free(toRawPtr[Captured[D]](mem))

    Intrinsics.storeObject(rawptr, value)

    GCRoots.addRoot(value)

    (mem, deallocate)
  }

end Captured

private[smithy4s_curl] object GCRoots:
  private val references = new java.util.IdentityHashMap[Object, Unit]
  def addRoot(o: Object): Unit = references.put(o, ())
  def removeRoot(o: Object): Unit = references.remove(o)
