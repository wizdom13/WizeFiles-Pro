package com.wisso.wizefiles.feature.share

import com.wisso.wizefiles.feature.sync.SyncPathResolver
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

internal class ShareGateway(
    private val profile: ShareProfile,
    roots: List<ShareRoot>,
    private val pathResolver: (String) -> Path? = SyncPathResolver::resolve
) {
    private val rootsById=roots.associateBy(ShareRoot::id)

    fun publicRoots():List<Pair<String,String>> = rootsById.values.map { it.id to it.alias }
    fun rootIdForToken(token:String):String? = rootsById.values.firstOrNull { it.id==token || it.alias.equals(token,true) }?.id
    val destructiveApprovalRequired:Boolean get()=profile.approveDestructiveInBrowser
    val permission:SharePermission get()=profile.permission
    val ftpWritable:Boolean get()=profile.ftpWritable && profile.permission==SharePermission.FULL_MANAGEMENT

    fun resolve(rootId:String,untrustedRelativePath:String,required:ShareCapability):ResolvedSharePath {
        val root=requireNotNull(rootsById[rootId]) { "Unknown shared root" }
        SharePathSecurity.requireShareableRoot(root.appPathUri)
        require(profile.permission.allows(required)) { "Profile permission denied" }
        require(rootAllows(root,required)) { "Provider capability denied" }
        val relative=SharePathSecurity.canonicalRelativePath(untrustedRelativePath)
        val rootPath=requireNotNull(pathResolver(root.appPathUri)) { "Shared root unavailable" }
        val child=relative.split('/').filter(String::isNotEmpty).fold(rootPath) { path,segment -> path.resolve(segment) }.normalize()
        require(child==rootPath.normalize() || child.startsWith(rootPath.normalize())) { "Path escaped shared root" }
        rejectEscapingSymlink(rootPath,child)
        return ResolvedSharePath(root,relative,child)
    }

    private fun rootAllows(root:ShareRoot,required:ShareCapability)=when(required){
        ShareCapability.READ->root.readable
        ShareCapability.CREATE->root.creatable
        ShareCapability.UPDATE->root.updatable
        ShareCapability.DELETE->root.deletable
    }

    private fun rejectEscapingSymlink(root:Path,child:Path) {
        val normalizedRoot=root.normalize()
        val normalizedChild=child.normalize()
        val existingPath=generateSequence(normalizedChild) { current ->
            current.parent?.takeIf { it.startsWith(normalizedRoot) }
        }.firstOrNull { Files.exists(it,LinkOption.NOFOLLOW_LINKS) } ?: return
        require(!Files.isSymbolicLink(existingPath)) { "Symbolic links are not shared" }
        val realRoot=try {
            normalizedRoot.toRealPath()
        } catch (exception:IOException) {
            throw IllegalArgumentException("Shared root cannot be verified",exception)
        }
        val realExistingPath=try {
            existingPath.toRealPath()
        } catch (exception:IOException) {
            throw IllegalArgumentException("Shared path cannot be verified",exception)
        }
        require(realExistingPath==realRoot || realExistingPath.startsWith(realRoot)) {
            "Symbolic-link escape"
        }
    }
}
