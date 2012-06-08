/*************************************************************************************
 * Copyright (c) 2012 VMware, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Andrew Eisenberg - Initial implementation.
 ************************************************************************************/
package org.codehaus.mojo.jsria;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.eclipse.m2e.jdt.AbstractJavaProjectConfigurator;
import org.eclipse.m2e.jdt.IClasspathDescriptor;
import org.eclipse.m2e.jdt.IClasspathManager;
import org.eclipse.wst.jsdt.core.IIncludePathAttribute;
import org.eclipse.wst.jsdt.core.IIncludePathEntry;
import org.eclipse.wst.jsdt.core.IJavaScriptProject;
import org.eclipse.wst.jsdt.core.JavaScriptCore;
import org.eclipse.wst.jsdt.internal.core.ClasspathAttribute;
import org.eclipse.wst.jsdt.internal.core.ClasspathEntry;
import org.jboss.tools.maven.apt.internal.utils.PluginDependencyResolver;

/**
 * Project configurator for JS RIA maven projects See
 * http://mojo.codehaus.org/javascript-maven-tools/javascript-ria-archetype/index.html
 * 
 * @author Andrew Eisenberg
 * @created May 29, 2012
 */
public class JsriaProjectConfigurator extends AbstractJavaProjectConfigurator {

    private static final ClasspathAttribute CLASSPATH_ATTRIBUTE = new ClasspathAttribute(IClasspathManager.POMDERIVED_ATTRIBUTE, Boolean.TRUE.toString());
    private PluginDependencyResolver pluginDependencyResolver = new PluginDependencyResolver();


    @Override
    public void configure(ProjectConfigurationRequest request,
            IProgressMonitor monitor) throws CoreException {
        monitor = SubMonitor.convert(monitor, "Configure JS RIA project", 3);
        
        IProject project = request.getProject();
        if (!project.hasNature(JavaScriptCore.NATURE_ID)) {
            addNature(project, JavaScriptCore.NATURE_ID, monitor);
        }

        // also add to the js classpath
        IJavaScriptProject jsProject = JavaScriptCore.create(project);
        IIncludePathEntry[] rawIncludepath = jsProject.getRawIncludepath();
        List<IIncludePathEntry> entries = new ArrayList<IIncludePathEntry>();
        for (int i = 0; i < rawIncludepath.length; i++) {
            // remove the project source entry as well as any entries that have the m2e include path attribtue
            if (!hasClasspathAttribute(rawIncludepath[i]) &&
                    !(rawIncludepath[i].getPath().segmentCount() == 1 && rawIncludepath[i].getEntryKind() == IIncludePathEntry.CPE_SOURCE)) {
                entries.add(rawIncludepath[i]);
            }
        }
        Set<Artifact> artifacts = request.getMavenProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            if (artifact.getType().equals("js")) {
                entries.add(createIncludePathEntry(artifact));
            }
        }
        IFolder srcMainJs = project.getFolder("src/main/js");
        if (srcMainJs.exists()) {
            entries.add(JavaScriptCore.newSourceEntry(srcMainJs.getFullPath(), ClasspathEntry.INCLUDE_ALL, ClasspathEntry.EXCLUDE_NONE, null, getClasspathAttribute()));
        }
        IFolder srcTestJs = project.getFolder("src/test/js");
        if (srcTestJs.exists()) {
            entries.add(JavaScriptCore.newSourceEntry(srcTestJs.getFullPath(), ClasspathEntry.INCLUDE_ALL, ClasspathEntry.EXCLUDE_NONE, null, getClasspathAttribute()));
        }
        jsProject.setRawIncludepath(entries.toArray(new IIncludePathEntry[entries.size()]), monitor);
    }

    @Override
    public void configureClasspath(IMavenProjectFacade facade,
            IClasspathDescriptor classpath, IProgressMonitor monitor)
            throws CoreException {
        super.configureClasspath(facade, classpath, monitor);
        
        List<File> files = pluginDependencyResolver.getResolvedPluginDependencies(
                createMavenSession(facade, monitor), facade.getMavenProject(), 
                Arrays.asList(createDependency("org.codehaus.jstestrunner", "jstestrunner-junit", "1.0.2"),
                        createDependency("junit", "junit", "4.9")), monitor);
        
        for (File file : files) {
            classpath.addLibraryEntry(new Path(file.getAbsolutePath()));
        }
    }

    @Override
    public void configureRawClasspath(ProjectConfigurationRequest request,
            IClasspathDescriptor classpath, IProgressMonitor monitor)
            throws CoreException {
        IProject project = request.getProject();
        IFolder srcMain = project.getFolder("src/main");
        IFolder srcMainJava = project.getFolder("src/main/java");
        IFolder srcTest = project.getFolder("src/test");
        IFolder srcTestJava = project.getFolder("src/test/java");
        if (!srcMain.exists()) {
            srcMain.create(true, true, monitor);
        }
        if (!srcMainJava.exists()) {
            srcMainJava.create(true, true, monitor);
        }
        if (!srcTest.exists()) {
            srcTest.create(true, true, monitor);
        }
        if (!srcTestJava.exists()) {
            srcTestJava.create(true, true, monitor);
        }
        super.configureRawClasspath(request, classpath, monitor);
    }

    @Override
    public AbstractBuildParticipant getBuildParticipant(
            IMavenProjectFacade projectFacade, MojoExecution execution,
            IPluginExecutionMetadata executionMetadata) {
        return new MojoExecutionBuildParticipant(execution, true);
    }

    @Override
    protected File[] getSourceFolders(ProjectConfigurationRequest request,
            MojoExecution mojoExecution) throws CoreException {
        return new File[0];
    }

    private Dependency createDependency(String group, String artifact, String version) {
        Dependency d = new Dependency();
        d.setArtifactId(artifact);
        d.setGroupId(group);
        d.setVersion(version);
        return d;
    }

    private IIncludePathEntry createIncludePathEntry(Artifact artifact) {
        return JavaScriptCore.newLibraryEntry(new Path(artifact.getFile().getAbsolutePath()), null, null, null, getClasspathAttribute(), true);
    }

    private MavenSession createMavenSession(IMavenProjectFacade facade, IProgressMonitor monitor) throws CoreException {
        MavenExecutionRequest request = projectManager.createExecutionRequest(facade.getPom(), facade.getResolverConfiguration(), monitor);
        return maven.createSession(request, facade.getMavenProject(monitor));
    }

    private IIncludePathAttribute[] getClasspathAttribute() {
        return new IIncludePathAttribute[] { CLASSPATH_ATTRIBUTE };
    }
    
    private boolean hasClasspathAttribute(IIncludePathEntry entry) {
        IIncludePathAttribute[] attrs = entry.getExtraAttributes();
        if (attrs == null || attrs.length == 0) {
            return false;
        }
        
        for (IIncludePathAttribute attr : attrs) {
            if (attr.equals(CLASSPATH_ATTRIBUTE)) {
                return true;
            }
        }
        return false;
    }

}
