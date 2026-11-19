package com.vegam.engine

/** Resume se pehle: kya server ki file wahi hai jo humne adhoori download ki thi? */
object ResumeValidator {
    fun canResume(saved: Validators, current: Validators): Boolean {
        if (saved.contentLength != current.contentLength) return false
        // ETag/Last-Modified tabhi compare karo jab dono taraf available ho.
        if (saved.etag != null && current.etag != null && saved.etag != current.etag) return false
        if (saved.lastModified != null && current.lastModified != null &&
            saved.lastModified != current.lastModified) return false
        return true
    }
}
