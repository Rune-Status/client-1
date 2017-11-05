@file:JvmName("LauncherMain")

package com.runesuite.client.launcher

import com.runesuite.client.common.*
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private val locator =  MavenRepositorySystemUtils.newServiceLocator().apply {
    addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
    addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
    addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
}

private val repoSystem = locator.getService(RepositorySystem::class.java)

private val localRepo = LocalRepository(Paths.get(System.getProperty("user.home"), ".m2", "repository").toFile())

private val session = MavenRepositorySystemUtils.newSession().apply {
    localRepositoryManager = repoSystem.newLocalRepositoryManager(this, localRepo)
}

private val remoteRepo = RemoteRepository.Builder(
        "repo.runesuite.com",
        "default",
        "http://repo.runesuite.com/libs-snapshot-local"
).build()

fun main(args: Array<String>) {
    Files.createDirectories(PLUGINS_SETTINGS_DIR_PATH)
    updateArtifact(PLUGINS_ARTIFACT_ID, PLUGINS_PATH)
    updateArtifact(CLIENT_ARTIFACT_ID, CLIENT_PATH)
    ProcessBuilder("java", "-jar", CLIENT_PATH.toString()).inheritIO().start().waitFor()
}

private fun updateArtifact(artifactId: String, path: Path) {
    val mvnPath = updateArtifactLocalRepo(artifactId)
    Files.createDirectories(path.parent)
    // throws exception if in use
    Files.copy(mvnPath, path, StandardCopyOption.REPLACE_EXISTING)
}

private fun updateArtifactLocalRepo(artifactId: String): Path {
    val artifact = DefaultArtifact(GROUP_ID, artifactId, "", "jar", "(,]")
    val versionRangeRequest = VersionRangeRequest(artifact, listOf(remoteRepo), null)
    val version = repoSystem.resolveVersionRange(session, versionRangeRequest).highestVersion.toString()
    val artifact2 = DefaultArtifact(GROUP_ID, artifactId, "", "jar", version)
    val artifactRequest = ArtifactRequest(artifact2, listOf(remoteRepo), null)
    return repoSystem.resolveArtifact(session, artifactRequest).artifact.file.toPath()
}