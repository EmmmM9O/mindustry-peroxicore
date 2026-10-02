package peroxicore.lwjgl

import arc.graphics.*
import arc.util.*
import org.lwjgl.opengles.*
import org.lwjgl.system.APIUtil.*
import org.lwjgl.system.MemoryStack.*
import org.lwjgl.system.MemoryUtil.*
import java.nio.*

class LwjglGL30 : LwjglGL20(), GL30 {
  override fun glReadBuffer(mode: Int) {
    GLES30.glReadBuffer(mode)
  }

  override fun glDrawRangeElements(
    mode: Int,
    start: Int,
    end: Int,
    count: Int,
    type: Int,
    indices: Buffer,
  ) {
    when (indices) {
      is ByteBuffer -> GLES30.glDrawRangeElements(mode, start, end, indices)
      is ShortBuffer -> GLES30.glDrawRangeElements(mode, start, end, indices)
      is IntBuffer -> GLES30.glDrawRangeElements(mode, start, end, indices)
      else -> throw ArcRuntimeException("indices must be byte, short or int buffer")
    }
  }

  override fun glDrawRangeElements(
    mode: Int,
    start: Int,
    end: Int,
    count: Int,
    type: Int,
    offset: Int,
  ) {
    GLES30.glDrawRangeElements(mode, start, end, count, type, offset.toLong())
  }

  override fun glTexImage3D(
    target: Int,
    level: Int,
    internalformat: Int,
    width: Int,
    height: Int,
    depth: Int,
    border: Int,
    format: Int,
    type: Int,
    pixels: Buffer?,
  ) {
    when (pixels) {
      null ->
        GLES30.glTexImage3D(
          target,
          level,
          internalformat,
          width,
          height,
          depth,
          border,
          format,
          type,
          null as ByteBuffer?,
        )

      is ByteBuffer ->
        GLES30.glTexImage3D(
          target,
          level,
          internalformat,
          width,
          height,
          depth,
          border,
          format,
          type,
          pixels,
        )

      is ShortBuffer ->
        GLES30.glTexImage3D(
          target,
          level,
          internalformat,
          width,
          height,
          depth,
          border,
          format,
          type,
          pixels,
        )

      is IntBuffer ->
        GLES30.glTexImage3D(
          target,
          level,
          internalformat,
          width,
          height,
          depth,
          border,
          format,
          type,
          pixels,
        )

      is FloatBuffer ->
        GLES30.glTexImage3D(
          target,
          level,
          internalformat,
          width,
          height,
          depth,
          border,
          format,
          type,
          pixels,
        )

      else ->
        throw ArcRuntimeException(
          "Can't use ${pixels.javaClass.name} with this method. Use ByteBuffer, ShortBuffer, IntBuffer, FloatBuffer or DoubleBuffer instead. Blame LWJGL"
        )
    }
  }

  override fun glTexImage3D(
    target: Int,
    level: Int,
    internalformat: Int,
    width: Int,
    height: Int,
    depth: Int,
    border: Int,
    format: Int,
    type: Int,
    offset: Int,
  ) {
    GLES30.glTexImage3D(
      target,
      level,
      internalformat,
      width,
      height,
      depth,
      border,
      format,
      type,
      offset.toLong(),
    )
  }

  override fun glTexSubImage3D(
    target: Int,
    level: Int,
    xoffset: Int,
    yoffset: Int,
    zoffset: Int,
    width: Int,
    height: Int,
    depth: Int,
    format: Int,
    type: Int,
    pixels: Buffer,
  ) {
    when (pixels) {
      is ByteBuffer ->
        GLES30.glTexSubImage3D(
          target,
          level,
          xoffset,
          yoffset,
          zoffset,
          width,
          height,
          depth,
          format,
          type,
          pixels,
        )

      is ShortBuffer ->
        GLES30.glTexSubImage3D(
          target,
          level,
          xoffset,
          yoffset,
          zoffset,
          width,
          height,
          depth,
          format,
          type,
          pixels,
        )

      is IntBuffer ->
        GLES30.glTexSubImage3D(
          target,
          level,
          xoffset,
          yoffset,
          zoffset,
          width,
          height,
          depth,
          format,
          type,
          pixels,
        )

      is FloatBuffer ->
        GLES30.glTexSubImage3D(
          target,
          level,
          xoffset,
          yoffset,
          zoffset,
          width,
          height,
          depth,
          format,
          type,
          pixels,
        )

      else ->
        throw ArcRuntimeException(
          "Can't use ${pixels.javaClass.name} with this method. Use ByteBuffer, ShortBuffer, IntBuffer, FloatBuffer or DoubleBuffer instead. Blame LWJGL"
        )
    }
  }

  override fun glTexSubImage3D(
    target: Int,
    level: Int,
    xoffset: Int,
    yoffset: Int,
    zoffset: Int,
    width: Int,
    height: Int,
    depth: Int,
    format: Int,
    type: Int,
    offset: Int,
  ) {
    GLES30.glTexSubImage3D(
      target,
      level,
      xoffset,
      yoffset,
      zoffset,
      width,
      height,
      depth,
      format,
      type,
      offset.toLong(),
    )
  }

  override fun glCopyTexSubImage3D(
    target: Int,
    level: Int,
    xoffset: Int,
    yoffset: Int,
    zoffset: Int,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ) {
    GLES30.glCopyTexSubImage3D(
      target,
      level,
      xoffset,
      yoffset,
      zoffset,
      x,
      y,
      width,
      height
    )
  }

  override fun glGenQueries(n: Int, ids: IntBuffer) {
    for (i in 0 until n) {
      ids.put(GLES30.glGenQueries())
    }
  }

  override fun glDeleteQueries(n: Int, ids: IntBuffer) {
    for (i in 0 until n) {
      GLES30.glDeleteQueries(ids.get())
    }
  }

  override fun glIsQuery(id: Int): Boolean {
    return GLES30.glIsQuery(id)
  }

  override fun glBeginQuery(target: Int, id: Int) {
    GLES30.glBeginQuery(target, id)
  }

  override fun glEndQuery(target: Int) {
    GLES30.glEndQuery(target)
  }

  override fun glGetQueryiv(target: Int, pname: Int, params: IntBuffer) {
    GLES30.glGetQueryiv(target, pname, params)
  }

  override fun glGetQueryObjectuiv(id: Int, pname: Int, params: IntBuffer) {
    GLES30.glGetQueryObjectuiv(id, pname, params)
  }

  override fun glUnmapBuffer(target: Int): Boolean {
    return GLES30.glUnmapBuffer(target)
  }

  override fun glGetBufferPointerv(target: Int, pname: Int): Buffer {
    // FIXME glGetBufferPointerv needs a proper translation
    // return GLES30.glGetBufferPointer(target, pname);
    throw UnsupportedOperationException("Not implemented")
  }

  override fun glDrawBuffers(n: Int, bufs: IntBuffer) {
    val limit = bufs.limit()
    (bufs as Buffer).limit(n)
    GLES30.glDrawBuffers(bufs)
    (bufs as Buffer).limit(limit)
  }

  override fun glUniformMatrix2x3fv(
    location: Int,
    count: Int,
    transpose: Boolean,
    value: FloatBuffer,
  ) {
    GLES30.glUniformMatrix2x3fv(location, transpose, value)
  }

  override fun glUniformMatrix3x2fv(
    location: Int,
    count: Int,
    transpose: Boolean,
    value: FloatBuffer,
  ) {
    GLES30.glUniformMatrix3x2fv(location, transpose, value)
  }

  override fun glUniformMatrix2x4fv(
    location: Int,
    count: Int,
    transpose: Boolean,
    value: FloatBuffer,
  ) {
    GLES30.glUniformMatrix2x4fv(location, transpose, value)
  }

  override fun glUniformMatrix4x2fv(
    location: Int,
    count: Int,
    transpose: Boolean,
    value: FloatBuffer,
  ) {
    GLES30.glUniformMatrix4x2fv(location, transpose, value)
  }

  override fun glUniformMatrix3x4fv(
    location: Int,
    count: Int,
    transpose: Boolean,
    value: FloatBuffer,
  ) {
    GLES30.glUniformMatrix3x4fv(location, transpose, value)
  }

  override fun glUniformMatrix4x3fv(
    location: Int,
    count: Int,
    transpose: Boolean,
    value: FloatBuffer,
  ) {
    GLES30.glUniformMatrix4x3fv(location, transpose, value)
  }

  override fun glBlitFramebuffer(
    srcX0: Int,
    srcY0: Int,
    srcX1: Int,
    srcY1: Int,
    dstX0: Int,
    dstY0: Int,
    dstX1: Int,
    dstY1: Int,
    mask: Int,
    filter: Int,
  ) {
    GLES30.glBlitFramebuffer(
      srcX0,
      srcY0,
      srcX1,
      srcY1,
      dstX0,
      dstY0,
      dstX1,
      dstY1,
      mask,
      filter
    )
  }

  override fun glBindFramebuffer(target: Int, framebuffer: Int) {
    GLES30.glBindFramebuffer(target, framebuffer)
  }

  override fun glBindRenderbuffer(target: Int, renderbuffer: Int) {
    GLES30.glBindRenderbuffer(target, renderbuffer)
  }

  override fun glCheckFramebufferStatus(target: Int): Int {
    return GLES30.glCheckFramebufferStatus(target)
  }

  /*
  override fun glDeleteFramebuffers(n: Int, framebuffers: IntBuffer) {
    GLES30.glDeleteFramebuffers(framebuffers)
  }*/
  override fun glDeleteFramebuffer(framebuffer: Int) {
    GLES30.glDeleteFramebuffers(framebuffer)
  }

  /*
  override fun glDeleteRenderbuffers(n: Int, renderbuffers: IntBuffer) {
    GLES30.glDeleteRenderbuffers(renderbuffers)
  }*/
  override fun glDeleteRenderbuffer(renderbuffer: Int) {
    GLES30.glDeleteRenderbuffers(renderbuffer)
  }

  override fun glGenerateMipmap(target: Int) {
    GLES30.glGenerateMipmap(target)
  }

  /*
  override fun glGenFramebuffers(n: Int, framebuffers: IntBuffer) {
    GLES30.glGenFramebuffers(framebuffers)
  }*/
  override fun glGenFramebuffer(): Int {
    return GLES30.glGenFramebuffers()
  }

  /*
  override fun glGenRenderbuffers(n: Int, renderbuffers: IntBuffer) {
    GLES30.glGenRenderbuffers(renderbuffers)
  }*/
  override fun glGenRenderbuffer(): Int {
    return GLES30.glGenRenderbuffers()
  }

  override fun glGetRenderbufferParameteriv(target: Int, pname: Int, params: IntBuffer) {
    GLES30.glGetRenderbufferParameteriv(target, pname, params)
  }

  override fun glIsFramebuffer(framebuffer: Int): Boolean {
    return GLES30.glIsFramebuffer(framebuffer)
  }

  override fun glIsRenderbuffer(renderbuffer: Int): Boolean {
    return GLES30.glIsRenderbuffer(renderbuffer)
  }

  override fun glRenderbufferStorage(
    target: Int,
    internalformat: Int,
    width: Int,
    height: Int
  ) {
    GLES30.glRenderbufferStorage(target, internalformat, width, height)
  }

  override fun glRenderbufferStorageMultisample(
    target: Int,
    samples: Int,
    internalformat: Int,
    width: Int,
    height: Int,
  ) {
    GLES30.glRenderbufferStorageMultisample(
      target,
      samples,
      internalformat,
      width,
      height
    )
  }

  override fun glFramebufferTexture2D(
    target: Int,
    attachment: Int,
    textarget: Int,
    texture: Int,
    level: Int,
  ) {
    GLES30.glFramebufferTexture2D(target, attachment, textarget, texture, level)
  }

  override fun glFramebufferRenderbuffer(
    target: Int,
    attachment: Int,
    renderbuffertarget: Int,
    renderbuffer: Int,
  ) {
    GLES30.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer)
  }

  override fun glFramebufferTextureLayer(
    target: Int,
    attachment: Int,
    texture: Int,
    level: Int,
    layer: Int,
  ) {
    GLES30.glFramebufferTextureLayer(target, attachment, texture, level, layer)
  }

  override fun glFlushMappedBufferRange(target: Int, offset: Int, length: Int) {
    GLES30.glFlushMappedBufferRange(target, offset.toLong(), length.toLong())
  }

  override fun glBindVertexArray(array: Int) {
    GLES30.glBindVertexArray(array)
  }

  override fun glDeleteVertexArrays(n: Int, arrays: IntBuffer) {
    GLES30.glDeleteVertexArrays(arrays)
  }

  override fun glGenVertexArrays(n: Int, arrays: IntBuffer) {
    GLES30.glGenVertexArrays(arrays)
  }

  override fun glIsVertexArray(array: Int): Boolean {
    return GLES30.glIsVertexArray(array)
  }

  override fun glBeginTransformFeedback(primitiveMode: Int) {
    GLES30.glBeginTransformFeedback(primitiveMode)
  }

  override fun glEndTransformFeedback() {
    GLES30.glEndTransformFeedback()
  }

  override fun glBindBufferRange(
    target: Int,
    index: Int,
    buffer: Int,
    offset: Int,
    size: Int
  ) {
    GLES30.glBindBufferRange(target, index, buffer, offset.toLong(), size.toLong())
  }

  override fun glBindBufferBase(target: Int, index: Int, buffer: Int) {
    GLES30.glBindBufferBase(target, index, buffer)
  }

  override fun glTransformFeedbackVaryings(
    program: Int,
    varyings: Array<String>,
    bufferMode: Int
  ) {
    GLES30.glTransformFeedbackVaryings(program, varyings, bufferMode)
  }

  override fun glVertexAttribIPointer(
    index: Int,
    size: Int,
    type: Int,
    stride: Int,
    offset: Int
  ) {
    GLES30.glVertexAttribIPointer(index, size, type, stride, offset.toLong())
  }

  override fun glGetVertexAttribIiv(index: Int, pname: Int, params: IntBuffer) {
    GLES30.glGetVertexAttribIiv(index, pname, params)
  }

  override fun glGetVertexAttribIuiv(index: Int, pname: Int, params: IntBuffer) {
    GLES30.glGetVertexAttribIuiv(index, pname, params)
  }

  override fun glVertexAttribI4i(index: Int, x: Int, y: Int, z: Int, w: Int) {
    GLES30.glVertexAttribI4i(index, x, y, z, w)
  }

  override fun glVertexAttribI4ui(index: Int, x: Int, y: Int, z: Int, w: Int) {
    GLES30.glVertexAttribI4ui(index, x, y, z, w)
  }

  override fun glGetUniformuiv(program: Int, location: Int, params: IntBuffer) {
    GLES30.glGetUniformuiv(program, location, params)
  }

  override fun glGetFragDataLocation(program: Int, name: String): Int {
    return GLES30.glGetFragDataLocation(program, name)
  }

  override fun glUniform1uiv(location: Int, count: Int, value: IntBuffer) {
    GLES30.glUniform1uiv(location, value)
  }

  override fun glUniform3uiv(location: Int, count: Int, value: IntBuffer) {
    GLES30.glUniform3uiv(location, value)
  }

  override fun glUniform4uiv(location: Int, count: Int, value: IntBuffer) {
    GLES30.glUniform4uiv(location, value)
  }

  override fun glClearBufferiv(buffer: Int, drawbuffer: Int, value: IntBuffer) {
    GLES30.glClearBufferiv(buffer, drawbuffer, value)
  }

  override fun glClearBufferuiv(buffer: Int, drawbuffer: Int, value: IntBuffer) {
    GLES30.glClearBufferuiv(buffer, drawbuffer, value)
  }

  override fun glClearBufferfv(buffer: Int, drawbuffer: Int, value: FloatBuffer) {
    GLES30.glClearBufferfv(buffer, drawbuffer, value)
  }

  override fun glClearBufferfi(buffer: Int, drawbuffer: Int, depth: Float, stencil: Int) {
    GLES30.glClearBufferfi(buffer, drawbuffer, depth, stencil)
  }

  override fun glGetStringi(name: Int, index: Int): String? {
    return GLES30.glGetStringi(name, index)
  }

  override fun glCopyBufferSubData(
    readTarget: Int,
    writeTarget: Int,
    readOffset: Int,
    writeOffset: Int,
    size: Int,
  ) {
    GLES30.glCopyBufferSubData(
      readTarget,
      writeTarget,
      readOffset.toLong(),
      writeOffset.toLong(),
      size.toLong(),
    )
  }

  override fun glGetUniformIndices(
    program: Int,
    uniformNames: Array<String>,
    uniformIndices: IntBuffer,
  ) {
    val stack = stackGet()
    val stackPointer = stack.pointer
    try {
      val uniformNamesAddress =
        apiArray(
          stack,
          { text, nullTerminated -> memASCII(text, nullTerminated) },
          *uniformNames,
        )
      GLES30.nglGetUniformIndices(
        program,
        uniformNames.size,
        uniformNamesAddress,
        memAddress(uniformIndices),
      )
      apiArrayFree(uniformNamesAddress, uniformNames.size)
    } finally {
      stack.pointer = stackPointer
    }
  }

  override fun glGetActiveUniformsiv(
    program: Int,
    uniformCount: Int,
    uniformIndices: IntBuffer,
    pname: Int,
    params: IntBuffer,
  ) {
    GLES30.glGetActiveUniformsiv(program, uniformIndices, pname, params)
  }

  override fun glGetUniformBlockIndex(program: Int, uniformBlockName: String): Int {
    return GLES30.glGetUniformBlockIndex(program, uniformBlockName)
  }

  override fun glGetActiveUniformBlockiv(
    program: Int,
    uniformBlockIndex: Int,
    pname: Int,
    params: IntBuffer,
  ) {
    GLES30.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params)
  }

  override fun glGetActiveUniformBlockName(
    program: Int,
    uniformBlockIndex: Int,
    length: Buffer,
    uniformBlockName: Buffer,
  ) {
    GLES30.glGetActiveUniformBlockName(
      program,
      uniformBlockIndex,
      length as IntBuffer,
      uniformBlockName as ByteBuffer,
    )
  }

  override fun glUniformBlockBinding(
    program: Int,
    uniformBlockIndex: Int,
    uniformBlockBinding: Int,
  ) {
    GLES30.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding)
  }

  override fun glDrawArraysInstanced(
    mode: Int,
    first: Int,
    count: Int,
    instanceCount: Int
  ) {
    GLES30.glDrawArraysInstanced(mode, first, count, instanceCount)
  }

  override fun glDrawElementsInstanced(
    mode: Int,
    count: Int,
    type: Int,
    indicesOffset: Int,
    instanceCount: Int,
  ) {
    GLES30.glDrawElementsInstanced(
      mode,
      count,
      type,
      indicesOffset.toLong(),
      instanceCount
    )
  }

  override fun glGetInteger64v(pname: Int, params: LongBuffer) {
    GLES30.glGetInteger64v(pname, params)
  }

  override fun glGetBufferParameteri64v(target: Int, pname: Int, params: LongBuffer) {
    GLES30.glGetBufferParameteri64v(target, pname, params)
  }

  override fun glGenSamplers(count: Int, samplers: IntBuffer) {
    GLES30.glGenSamplers(samplers)
  }

  override fun glDeleteSamplers(count: Int, samplers: IntBuffer) {
    GLES30.glDeleteSamplers(samplers)
  }

  override fun glIsSampler(sampler: Int): Boolean {
    return GLES30.glIsSampler(sampler)
  }

  override fun glBindSampler(unit: Int, sampler: Int) {
    GLES30.glBindSampler(unit, sampler)
  }

  override fun glSamplerParameteri(sampler: Int, pname: Int, param: Int) {
    GLES30.glSamplerParameteri(sampler, pname, param)
  }

  override fun glSamplerParameteriv(sampler: Int, pname: Int, param: IntBuffer) {
    GLES30.glSamplerParameteriv(sampler, pname, param)
  }

  override fun glSamplerParameterf(sampler: Int, pname: Int, param: Float) {
    GLES30.glSamplerParameterf(sampler, pname, param)
  }

  override fun glSamplerParameterfv(sampler: Int, pname: Int, param: FloatBuffer) {
    GLES30.glSamplerParameterfv(sampler, pname, param)
  }

  override fun glGetSamplerParameteriv(sampler: Int, pname: Int, params: IntBuffer) {
    GLES30.glGetSamplerParameteriv(sampler, pname, params)
  }

  override fun glGetSamplerParameterfv(sampler: Int, pname: Int, params: FloatBuffer) {
    GLES30.glGetSamplerParameterfv(sampler, pname, params)
  }

  override fun glVertexAttribDivisor(index: Int, divisor: Int) {
    GLES30.glVertexAttribDivisor(index, divisor)
  }

  override fun glBindTransformFeedback(target: Int, id: Int) {
    GLES30.glBindTransformFeedback(target, id)
  }

  override fun glDeleteTransformFeedbacks(n: Int, ids: IntBuffer) {
    GLES30.glDeleteTransformFeedbacks(ids)
  }

  override fun glGenTransformFeedbacks(n: Int, ids: IntBuffer) {
    GLES30.glGenTransformFeedbacks(ids)
  }

  override fun glIsTransformFeedback(id: Int): Boolean {
    return GLES30.glIsTransformFeedback(id)
  }

  override fun glPauseTransformFeedback() {
    GLES30.glPauseTransformFeedback()
  }

  override fun glResumeTransformFeedback() {
    GLES30.glResumeTransformFeedback()
  }

  override fun glProgramParameteri(program: Int, pname: Int, value: Int) {
    GLES30.glProgramParameteri(program, pname, value)
  }

  override fun glInvalidateFramebuffer(
    target: Int,
    numAttachments: Int,
    attachments: IntBuffer
  ) {
    GLES30.glInvalidateFramebuffer(target, attachments)
  }

  override fun glInvalidateSubFramebuffer(
    target: Int,
    numAttachments: Int,
    attachments: IntBuffer,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
  ) {
    GLES30.glInvalidateSubFramebuffer(target, attachments, x, y, width, height)
  }
}
