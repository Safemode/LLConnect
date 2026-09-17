package com.safemode.llconnect.data.remote.models

data class ExtraField(
    val name: String? = null,
    val value: String? = null,
)

data class FileAttachment(
    val name: String? = null,
    val location: String? = null,
)

data class FileAttachmentResponse(
    val name: String? = null,
    val location: String? = null,
    val isPending: Boolean? = null,
)

/** Response of GET /api/whoami */
data class WhoAmI(
    val id: String? = null,
    val userName: String? = null,
    val emailAddress: String? = null,
    val isAdmin: Boolean? = null,
    val isRootUser: Boolean? = null,
)
