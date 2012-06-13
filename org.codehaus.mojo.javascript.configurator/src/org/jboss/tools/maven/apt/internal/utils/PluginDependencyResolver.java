/*************************************************************************************
 * Copyright (c) 2008-2012 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Red Hat, Inc. - Initial implementation.
 *     Andrew Eisenberg - Adapted for the JsRia configurator
 ************************************************************************************/
package org.jboss.tools.maven.apt.internal.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.javascript.configurator.JsriaActivator;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.internal.MavenPluginActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.artifact.ArtifactTypeRegistry;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.DependencyGraphTransformer;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.util.DefaultRepositorySystemSession;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.DependencyFilterUtils;
import org.sonatype.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.sonatype.aether.util.graph.transformer.JavaEffectiveScopeCalculator;
import org.sonatype.aether.util.graph.transformer.NearestVersionConflictResolver;

public class PluginDependencyResolver {

    private static final Logger log = LoggerFactory.getLogger(PluginDependencyResolver.class);

    /**
     * Looks up a plugin's dependencies (including the transitive ones) and return them as a list of {@link File} 
     * <br/>
     * Some of {@link MavenModelManager#readDependencyTree(org.eclipse.m2e.core.project.IMavenProjectFacade, MavenProject, String, IProgressMonitor)}'s logic has been copied and reused in this implementation.
     */
    public synchronized List<File> getResolvedPluginDependencies(MavenSession mavenSession, MavenProject mavenProject, List<org.apache.maven.model.Dependency> dependencies, IProgressMonitor monitor) throws CoreException {
        List<ArtifactResult> artifactResults = internalGetResolvedPluginArtifacts(mavenSession, mavenProject, dependencies, monitor);
        List<File> files = new ArrayList<File>();
        for (ArtifactResult artifactResult : artifactResults) {
            files.add(artifactResult.getArtifact().getFile());
        }
        return files;
    }
    public synchronized List<Artifact> getResolvedPluginArtifacts(MavenSession mavenSession, MavenProject mavenProject, List<org.apache.maven.model.Dependency> dependencies, IProgressMonitor monitor) throws CoreException {
        List<ArtifactResult> artifactResults = internalGetResolvedPluginArtifacts(mavenSession, mavenProject, dependencies, monitor);
        List<Artifact> artifacts = new ArrayList<Artifact>();
        for (ArtifactResult artifactResult : artifactResults) {
            org.sonatype.aether.artifact.Artifact artifact = artifactResult.getArtifact();
            try {
                artifacts.add(new DefaultArtifact(
                        artifact.getGroupId(), 
                        artifact.getArtifactId(), 
                        VersionRange.createFromVersionSpec(artifact.getVersion()), 
                        "build", 
                        "jar", 
                        artifact.getClassifier(), 
                        (ArtifactHandler) null, 
                        false));
            } catch (InvalidVersionSpecificationException e) {
                e.printStackTrace();
            }
        }
        return artifacts;
    }

    private synchronized List<ArtifactResult> internalGetResolvedPluginArtifacts(MavenSession mavenSession, MavenProject mavenProject, List<org.apache.maven.model.Dependency> dependencies, IProgressMonitor monitor) throws CoreException {
        monitor.setTaskName("Resolve plugin dependency");

        IMaven maven = MavenPlugin.getMaven();

        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(mavenSession.getRepositorySession());

        DependencyGraphTransformer transformer = new ChainedDependencyGraphTransformer(new JavaEffectiveScopeCalculator(),
                new NearestVersionConflictResolver());
        session.setDependencyGraphTransformer(transformer);

        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(maven.getProjectRealm(mavenProject));

            ArtifactTypeRegistry stereotypes = session.getArtifactTypeRegistry();

            CollectRequest request = new CollectRequest();
            // FIXADE do we want the plugin context here???
            request.setRequestContext("plugin"); //$NON-NLS-1$
            request.setRepositories(mavenProject.getRemoteProjectRepositories());

            for(org.apache.maven.model.Dependency dependency : dependencies) {
                request.addDependency(RepositoryUtils.toDependency(dependency, stereotypes));
            }

            DependencyFilter classpathFilter = DependencyFilterUtils.classpathFilter( JavaScopes.COMPILE, JavaScopes.RUNTIME, JavaScopes.TEST);

            DependencyRequest dependencyRequest = new DependencyRequest( request, classpathFilter );
            try {
                RepositorySystem system = MavenPluginActivator.getDefault().getRepositorySystem(); 
                List<ArtifactResult> artifactResults = system.resolveDependencies( session, dependencyRequest ).getArtifactResults();
                return artifactResults;

            } catch(DependencyResolutionException e) {
                String msg = "Unable to collect transitive dependencies";
                log.error(msg, e);
                throw new CoreException(new Status(IStatus.ERROR, JsriaActivator.PLUGIN_ID, -1, msg, e));
            }

        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }
}