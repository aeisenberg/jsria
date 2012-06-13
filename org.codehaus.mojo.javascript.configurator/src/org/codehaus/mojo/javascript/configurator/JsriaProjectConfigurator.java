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
package org.codehaus.mojo.javascript.configurator;

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
import org.apache.maven.project.MavenProject;
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

    private static final Dependency JUNIT_DEPENDENCY = createDependency("junit", "junit", "4.9");
    private static final Dependency JSTESTRUNNER_DEPENDENCY = createDependency("org.codehaus.jstestrunner", "jstestrunner-junit", "1.0.2");
    private static final Dependency QUNIT_DEPENDENCY = createDependency("org.codehaus.mojo", "qunit-amd", "1.5.0-alpha-1", "test", "zip", "www");
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
            // remove project source entries as well as any entries that have the m2e include path attribtue
            if (!hasClasspathAttribute(rawIncludepath[i]) &&
                    !(rawIncludepath[i].getPath().segmentCount() == 1 && rawIncludepath[i].getEntryKind() == IIncludePathEntry.CPE_SOURCE)) {
                entries.add(rawIncludepath[i]);
            }
        }
        MavenProject mavenProject = request.getMavenProject();
        Set<Artifact> artifacts = mavenProject.getArtifacts();
        for (Artifact artifact : artifacts) {
            if (artifact.getType().equals("js")) {
                entries.add(createIncludePathEntry(artifact));
            }
        }
        createSourceFolders("js", project, monitor);
        IFolder srcMainJs = project.getFolder("src/main/js");
        IFolder srcTestJs = project.getFolder("src/test/js");
        entries.add(JavaScriptCore.newSourceEntry(srcMainJs.getFullPath(), ClasspathEntry.INCLUDE_ALL, ClasspathEntry.EXCLUDE_NONE, null, getClasspathAttribute()));
        entries.add(JavaScriptCore.newSourceEntry(srcTestJs.getFullPath(), ClasspathEntry.INCLUDE_ALL, ClasspathEntry.EXCLUDE_NONE, null, getClasspathAttribute()));
        jsProject.setRawIncludepath(entries.toArray(new IIncludePathEntry[entries.size()]), monitor);
        
        ensureImplicitDependencies(mavenProject);
    }

    @Override
    public void configureClasspath(IMavenProjectFacade facade,
            IClasspathDescriptor classpath, IProgressMonitor monitor)
            throws CoreException {
        super.configureClasspath(facade, classpath, monitor);
        ensureImplicitDependencies(facade.getMavenProject());
        
        List<File> files = pluginDependencyResolver.getResolvedPluginDependencies(
                createMavenSession(facade, monitor), facade.getMavenProject(), 
                Arrays.asList(JSTESTRUNNER_DEPENDENCY,
                        JUNIT_DEPENDENCY), monitor);
        
        for (File file : files) {
            classpath.addLibraryEntry(new Path(file.getAbsolutePath()));
        }
    }

    @Override
    public void configureRawClasspath(ProjectConfigurationRequest request,
            IClasspathDescriptor classpath, IProgressMonitor monitor)
            throws CoreException {
        ensureImplicitDependencies(request.getMavenProject());
        IProject project = request.getProject();
        createSourceFolders("java", project, monitor);
        super.configureRawClasspath(request, classpath, monitor);
    }

    @Override
    public AbstractBuildParticipant getBuildParticipant(
            IMavenProjectFacade projectFacade, MojoExecution execution,
            IPluginExecutionMetadata executionMetadata) {
        ensureImplicitDependencies(projectFacade.getMavenProject());
        return new MojoExecutionBuildParticipant(execution, true);
    }

    @Override
    protected File[] getSourceFolders(ProjectConfigurationRequest request,
            MojoExecution mojoExecution) throws CoreException {
        ensureImplicitDependencies(request.getMavenProject());
        return new File[0];
    }

    private void ensureImplicitDependencies(MavenProject mavenProject) {
        List<Dependency> dependencies = mavenProject.getDependencies();
        boolean qunitExists = false, jstestRunnerExists = false, junitExists = false;
        for (Dependency d : dependencies) {
            if (!qunitExists && dependencyEquivalent(d, QUNIT_DEPENDENCY)) {
                qunitExists = true;
            } else if (!jstestRunnerExists && dependencyEquivalent(d, JSTESTRUNNER_DEPENDENCY)) {
                jstestRunnerExists = true;
            } else if (!junitExists && dependencyEquivalent(d, JUNIT_DEPENDENCY)) {
                junitExists = true;
            }
        }
        
        if (!qunitExists) {
            dependencies.add(QUNIT_DEPENDENCY);
        }
        if (!jstestRunnerExists) {
            dependencies.add(JSTESTRUNNER_DEPENDENCY);
        }
        if (!junitExists) {
            dependencies.add(JUNIT_DEPENDENCY);
        }
    }

    private boolean dependencyEquivalent(Dependency d1, Dependency d2) {
        return d1.getArtifactId().equals(d2.getArtifactId()) && d1.getGroupId().equals(d2.getGroupId());
    }

    private void createSourceFolders(String name, IProject project, IProgressMonitor monitor)
            throws CoreException {
        IFolder srcMain = project.getFolder("src/main");
        IFolder srcMainJava = srcMain.getFolder(name);
        IFolder srcTest = project.getFolder("src/test");
        IFolder srcTestJava = srcTest.getFolder(name);
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

    private static Dependency createDependency(String group, String artifact, String version, String scope, String type, String classifier) {
        Dependency d = createDependency(group, artifact, version);
        d.setScope(scope);
        d.setType(type);
        d.setClassifier(classifier);
        return d;
    }

    private static Dependency createDependency(String group, String artifact, String version) {
        Dependency d = new Dependency();
        d.setArtifactId(artifact);
        d.setGroupId(group);
        d.setVersion(version);
        return d;
    }

}
