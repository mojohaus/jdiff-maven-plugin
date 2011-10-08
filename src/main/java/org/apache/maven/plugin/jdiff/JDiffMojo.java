package org.apache.maven.plugin.jdiff;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.lang.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

/**
 * @goal jdiff
 * @execute phase="generate-sources"
 * @requiresDependencyResolution compile 
 * @description A Maven 2.0 JDiff plugin to generate an api difference report between SCM versions
 */
public class JDiffMojo
    extends AbstractMavenReport
{

    /**
     * Sets the absolute path of the Javadoc Tool executable to use. Since version 2.5, a mere directory specification
     * is sufficient to have the plugin use "javadoc" or "javadoc.exe" respectively from this directory.
     *
     * @parameter expression="${javadocExecutable}"
     */
    private String javadocExecutable;
    
    /**
     * Version to compare the current code against.
     * 
     * @parameter expression="${comparisonVersion}" default-value="(,${project.version})"
     */
    private String comparisonVersion;

    /**
     * @parameter default-value="${project.reporting.outputDirectory}/jdiff"
     * @required
     * @readonly
     */
    private String reportingOutputDirectory;
    
    /**
     * @parameter default-value="${project.build.outputDirectory}"
     * @required
     * @readonly
     */
    private String buildOutputDirectory;

    /**
     * The current build session instance. This is used for
     * toolchain manager API calls.
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;
    
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @parameter default-value="${plugin.artifacts}"
     * @required
     * @readonly
     */
    private List<Artifact> pluginArtifacts;

    /** @component */
    private ToolchainManager toolchainManager;
    
    /** @component */
    private Renderer siteRenderer;

    /** @component */
    private ScmManager scmManager;

    /** @component */
    private ArtifactMetadataSource metadataSource;

    /** @component */
    private ArtifactFactory factory;

    /**
     * The local repository where the artifacts are located.
     * 
     * @parameter expression="${localRepository}"
     */
    private ArtifactRepository localRepository;

    /**
     * The remote repositories where artifacts are located.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    private List<ArtifactRepository> remoteRepositories;

    /** @component */
    private MavenProjectBuilder mavenProjectBuilder;

    private Set<String> packages = new HashSet<String>();
    
    public void executeReport( Locale locale ) throws MavenReportException
    {
        //tmp
        JDiffUtils.setLogger( getLog() );
        
        MavenProject lhsProject, rhsProject;
        try
        {
            lhsProject = resolveProject( getComparisonArtifact() );
            rhsProject = project;
        }
        catch ( ProjectBuildingException e )
        {
            throw new MavenReportException( e.getMessage() );
        }
        catch ( MojoFailureException e )
        {
            throw new MavenReportException( e.getMessage() );
        }
        catch ( MojoExecutionException e )
        {
            throw new MavenReportException( e.getMessage() );
        }
        
        String lhsTag = lhsProject.getVersion();
        
        String rhsTag = rhsProject.getVersion();
        String rhsBaseDirectory = getSrcDir( rhsTag, rhsProject );

        generateJDiffXML( lhsProject, lhsTag );
        generateJDiffXML( rhsProject, rhsTag );
        
        generateReport( rhsBaseDirectory, lhsTag, rhsTag );
        
        new JDiffReportGenerator().doGenerateReport( getBundle( locale ), getSink() );
    }
    
    

    private MavenProject resolveProject( Artifact comparisonArtifact ) throws MojoFailureException, MojoExecutionException, ProjectBuildingException, MavenReportException
    {
        MavenProject externalProject = mavenProjectBuilder.buildFromRepository( getComparisonArtifact(), remoteRepositories, localRepository );
        
        String checkoutDirectory = reportingOutputDirectory + "/" + externalProject.getVersion();
        doCheckout( externalProject.getVersion(), checkoutDirectory, externalProject );
        
        ProfileManager profileManager = null;
        
        return mavenProjectBuilder.build( new File(checkoutDirectory, "pom.xml"), localRepository, profileManager );
    }



    private String getSrcDir( String version, MavenProject mavenProject )
        throws MavenReportException
    {
        String srcDir;

        if ( version.equals( project.getVersion() ) )
        {
            srcDir = project.getBuild().getSourceDirectory();
        }
        else
        {
            doCheckout( version, reportingOutputDirectory + "/" + version, mavenProject );

            srcDir = reportingOutputDirectory + "/" + version + "/src/main/java";
        }

        return srcDir;
    }

    private String getConnection( MavenProject mavenProject )
        throws MavenReportException
    {
        if ( mavenProject.getScm() == null )
            throw new MavenReportException( "SCM Connection is not set in your pom.xml." );

        String connection = mavenProject.getScm().getConnection();

        if ( connection != null )
        {
            if ( connection.length() > 0 )
            {
                return connection;
            }
        }
        connection = mavenProject.getScm().getDeveloperConnection();

        if ( connection == null )
        {
            throw new MavenReportException( "SCM Connection is not set in your pom.xml." );
        }
        if ( connection.length() == 0 )
        {
            throw new MavenReportException( "SCM Connection is not set in your pom.xml." );
        }
        return connection;
    }

    private void doCheckout( String tag, String checkoutDir, MavenProject mavenProject )
        throws MavenReportException
    {
        try
        {

            File dir = new File( checkoutDir );

            // @todo remove when scm update is to be used
            if ( /* forceCheckout  */ dir.exists() )
            {
                FileUtils.deleteDirectory( dir );
            }

            if ( dir.exists() || dir.mkdirs() )
            {

                getLog().info( "Performing checkout to " + checkoutDir );

                new ScmBean( scmManager, getConnection( mavenProject ) ).checkout( checkoutDir );
            }
            else
            {
                getLog().info( "Performing update to " + checkoutDir );

                new ScmBean( scmManager, getConnection( mavenProject ) ).update( checkoutDir );
            }
        }
        catch ( Exception ex )
        {
            throw new MavenReportException( "checkout failed.", ex );
        }
    }

    private void generateJDiffXML( MavenProject project, String tag )
        throws MavenReportException
    {
        try
        {
            JavadocBean javadoc = new JavadocBean( getJavadocExecutable() );

            javadoc.addArgumentPair( "doclet", "jdiff.JDiff" );

            javadoc.addArgumentPair( "docletpath", getPluginClasspath() );

            javadoc.addArgumentPair( "apiname", tag );

            javadoc.addArgumentPair( "apidir", reportingOutputDirectory );

            List<String> classpathElements = new ArrayList<String>();
            classpathElements.add( buildOutputDirectory );
            classpathElements.addAll( JDiffUtils.getClasspathElements( project ) );
            String classpath = StringUtils.join( classpathElements.iterator(), File.pathSeparator );
            javadoc.addArgumentPair( "classpath", StringUtils.quoteAndEscape( classpath, '\'' ) );

            String sourcePath = StringUtils.join( JDiffUtils.getProjectSourceRoots( project ).iterator(), File.pathSeparator );
            javadoc.addArgumentPair( "sourcepath", StringUtils.quoteAndEscape( sourcePath, '\'' ) );

            Set<String> pckgs = JDiffUtils.getPackages( project ); 
            for( String pckg : pckgs )
            {
                javadoc.addArgument( pckg );
            }
            packages.addAll( pckgs );
            
            javadoc.execute( reportingOutputDirectory );
        }
        catch ( IOException e )
        {
            throw new MavenReportException( e.getMessage(), e );
        }
    }

    private String getPluginClasspath()
    {
        String cp = "";

        for ( Artifact artifact : pluginArtifacts )
        {
            cp += ";" + artifact.getFile().getAbsolutePath();
        }

        return cp.length() > 0 ? cp.substring( 1 ) : cp;
    }

    private void generateReport( String srcDir, String oldApi, String newApi )
        throws MavenReportException
    {
        try
        {
            JavadocBean javadoc = new JavadocBean( getJavadocExecutable() );

            javadoc.addArgument( "-private" );

            javadoc.addArgumentPair( "d", reportingOutputDirectory );

            javadoc.addArgumentPair( "sourcepath", srcDir );

            List<String> classpathElements = new ArrayList<String>();
            classpathElements.add( buildOutputDirectory );
            classpathElements.addAll( JDiffUtils.getClasspathElements( project ) );
            String classpath = StringUtils.join( classpathElements.iterator(), File.pathSeparator );
            javadoc.addArgumentPair( "classpath", StringUtils.quoteAndEscape( classpath, '\'' ) );

            javadoc.addArgumentPair( "doclet", "jdiff.JDiff" );

            javadoc.addArgumentPair( "docletpath", getPluginClasspath() );

            javadoc.addArgumentPair( "oldapi", oldApi );

            javadoc.addArgumentPair( "newapi", newApi );

            javadoc.addArgument( "-stats" );

            for ( String pckg : packages )
            {
                javadoc.addArgument( pckg );
            }

            javadoc.execute( reportingOutputDirectory );
        }
        catch ( IOException e )
        {
            throw new MavenReportException( e.getMessage(), e );
        }
    }

    @Override
    protected MavenProject getProject()
    {
        return project;
    }

    @Override
    protected Renderer getSiteRenderer()
    {
        return siteRenderer;
    }

    @Override
    protected String getOutputDirectory()
    {
        return reportingOutputDirectory;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.maven.reporting.MavenReport#getDescription(java.util.Locale)
     */
    public String getDescription( Locale locale )
    {
        return "Maven 2 JDiff Plugin";
    }

    /*
     * (non-Javadoc)
     * @see org.apache.maven.reporting.MavenReport#getName(java.util.Locale)
     */
    public String getName( Locale locale )
    {
        return "JDiff";
    }

    /*
     * (non-Javadoc)
     * @see org.apache.maven.reporting.MavenReport#getOutputName()
     */
    public String getOutputName()
    {
        return "jdiff";
    }

    private Artifact getComparisonArtifact()
        throws MojoFailureException, MojoExecutionException
    {
        // Find the previous version JAR and resolve it, and it's dependencies
        VersionRange range;
        try
        {
            range = VersionRange.createFromVersionSpec( comparisonVersion );
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new MojoFailureException( "Invalid comparison version: " + e.getMessage() );
        }

        Artifact previousArtifact;
        try
        {
            previousArtifact =
                factory.createDependencyArtifact( project.getGroupId(), project.getArtifactId(), range,
                                                  project.getPackaging(), null, Artifact.SCOPE_COMPILE );

            if ( !previousArtifact.getVersionRange().isSelectedVersionKnown( previousArtifact ) )
            {
                getLog().debug( "Searching for versions in range: " + previousArtifact.getVersionRange() );
                List<ArtifactVersion> availableVersions =
                    metadataSource.retrieveAvailableVersions( previousArtifact, localRepository,
                                                              project.getRemoteArtifactRepositories() );
                filterSnapshots( availableVersions );
                ArtifactVersion version = range.matchVersion( availableVersions );
                if ( version != null )
                {
                    previousArtifact.selectVersion( version.toString() );
                }
            }
        }
        catch ( OverConstrainedVersionException e1 )
        {
            throw new MojoFailureException( "Invalid comparison version: " + e1.getMessage() );
        }
        catch ( ArtifactMetadataRetrievalException e11 )
        {
            throw new MojoExecutionException( "Error determining previous version: " + e11.getMessage(), e11 );
        }

        if ( previousArtifact.getVersion() == null )
        {
            getLog().info( "Unable to find a previous version of the project in the repository" );
        }
        else
        {
            getLog().debug( "Previous version: " + previousArtifact.getVersion() );
        }

        return previousArtifact;
    }

    private void filterSnapshots( List<ArtifactVersion> versions )
    {
        for ( Iterator<ArtifactVersion> versionIterator = versions.iterator(); versionIterator.hasNext(); )
        {
            if ( "SNAPSHOT".equals( versionIterator.next().getQualifier() ) )
            {
                versionIterator.remove();
            }
        }
    }
    
    private ResourceBundle getBundle( Locale locale )
    {
        return ResourceBundle.getBundle( "jdiff-report", locale, this.getClass().getClassLoader() );
    }

    //Borrowed from maven-javadoc-plugin
    /**
     * Get the path of the Javadoc tool executable depending the user entry or try to find it depending the OS
     * or the <code>java.home</code> system property or the <code>JAVA_HOME</code> environment variable.
     *
     * @return the path of the Javadoc tool
     * @throws IOException if not found
     */
    private String getJavadocExecutable()
        throws IOException
    {
        Toolchain tc = getToolchain();

        if ( tc != null )
        {
            getLog().info( "Toolchain in javadoc-plugin: " + tc );
            if ( javadocExecutable != null )
            {
                getLog().warn(
                               "Toolchains are ignored, 'javadocExecutable' parameter is set to "
                                   + javadocExecutable );
            }
            else
            {
                javadocExecutable = tc.findTool( "javadoc" );
            }
        }

        String javadocCommand = "javadoc" + ( SystemUtils.IS_OS_WINDOWS ? ".exe" : "" );

        File javadocExe;

        // ----------------------------------------------------------------------
        // The javadoc executable is defined by the user
        // ----------------------------------------------------------------------
        if ( StringUtils.isNotEmpty( javadocExecutable ) )
        {
            javadocExe = new File( javadocExecutable );

            if ( javadocExe.isDirectory() )
            {
                javadocExe = new File( javadocExe, javadocCommand );
            }

            if ( SystemUtils.IS_OS_WINDOWS && javadocExe.getName().indexOf( '.' ) < 0 )
            {
                javadocExe = new File( javadocExe.getPath() + ".exe" );
            }

            if ( !javadocExe.isFile() )
            {
                throw new IOException( "The javadoc executable '" + javadocExe
                    + "' doesn't exist or is not a file. Verify the <javadocExecutable/> parameter." );
            }

            return javadocExe.getAbsolutePath();
        }

        // ----------------------------------------------------------------------
        // Try to find javadocExe from System.getProperty( "java.home" )
        // By default, System.getProperty( "java.home" ) = JRE_HOME and JRE_HOME
        // should be in the JDK_HOME
        // ----------------------------------------------------------------------
        // For IBM's JDK 1.2
        if ( SystemUtils.IS_OS_AIX )
        {
            javadocExe =
                new File( SystemUtils.getJavaHome() + File.separator + ".." + File.separator + "sh",
                          javadocCommand );
        }
        else if ( SystemUtils.IS_OS_MAC_OSX )
        {
            javadocExe = new File( SystemUtils.getJavaHome() + File.separator + "bin", javadocCommand );
        }
        else
        {
            javadocExe =
                new File( SystemUtils.getJavaHome() + File.separator + ".." + File.separator + "bin",
                          javadocCommand );
        }

        // ----------------------------------------------------------------------
        // Try to find javadocExe from JAVA_HOME environment variable
        // ----------------------------------------------------------------------
        if ( !javadocExe.exists() || !javadocExe.isFile() )
        {
            Properties env = CommandLineUtils.getSystemEnvVars();
            String javaHome = env.getProperty( "JAVA_HOME" );
            if ( StringUtils.isEmpty( javaHome ) )
            {
                throw new IOException( "The environment variable JAVA_HOME is not correctly set." );
            }
            if ( ( !new File( javaHome ).exists() ) || ( !new File( javaHome ).isDirectory() ) )
            {
                throw new IOException( "The environment variable JAVA_HOME=" + javaHome
                    + " doesn't exist or is not a valid directory." );
            }

            javadocExe = new File( env.getProperty( "JAVA_HOME" ) + File.separator + "bin", javadocCommand );
        }

        if ( !javadocExe.exists() || !javadocExe.isFile() )
        {
            throw new IOException( "The javadoc executable '" + javadocExe
                + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable." );
        }

        return javadocExe.getAbsolutePath();
    }
    
    private Toolchain getToolchain()
    {
        Toolchain tc = null;
        if ( toolchainManager != null )
        {
            tc = toolchainManager.getToolchainFromBuildContext( "jdk", session );
        }

        return tc;
    }
}