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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.scm.manager.ScmManager;
import org.codehaus.plexus.util.FileUtils;

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
     * @parameter default-value="${project.groupId}"
     * @required
     */
    private String packages;

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
    private String outputDirectory;

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

    /**
     * @component
     */
    private Renderer siteRenderer;

    /**
     * @component
     */
    private ScmManager scmManager;

    /**
     * @component
     */
    private ArtifactMetadataSource metadataSource;

    /**
     * @component
     */
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

    /**
     * @component
     */
    private MavenProjectBuilder mavenProjectBuilder;

    public void executeReport( Locale locale ) throws MavenReportException
    {
        MavenProject lhsProject, rhsProject;
        try
        {
            lhsProject =
                mavenProjectBuilder.buildFromRepository( getComparisonArtifact(), remoteRepositories, localRepository );
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
        getLog().debug( "lhsTag:" + lhsTag );
        String lhsSource = getSrcDir( lhsTag, lhsProject );
        
        String rhsTag = rhsProject.getVersion();
        getLog().debug( "rhsTag:" + rhsTag );
        String rhsSource = getSrcDir( rhsTag, rhsProject );

        generateJDiffXML( lhsSource, lhsTag );
        generateJDiffXML( rhsSource, rhsTag );
        
        generateReport( rhsSource, lhsTag, rhsTag );
        
        new JDiffReportGenerator().doGenerateReport( getBundle( locale ), getSink() );
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
            doCheckout( version, outputDirectory + "/" + version, mavenProject );

            srcDir = outputDirectory + "/" + version + "/src/main/java";
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

    private void generateJDiffXML( String srcDir, String tag )
        throws MavenReportException
    {
        JavadocBean javadoc = new JavadocBean();

        javadoc.addArgumentPair( "doclet", "jdiff.JDiff" );

        javadoc.addArgumentPair( "docletpath", getPluginClasspath() );

        javadoc.addArgumentPair( "apiname", tag );

        javadoc.addArgumentPair( "apidir", outputDirectory );

        javadoc.addArgumentPair( "classpath", getProjectClasspath() );

        javadoc.addArgumentPair( "sourcepath", srcDir );

        javadoc.addArgument( packages );

        javadoc.execute( outputDirectory );
    }

    private String getProjectClasspath()
    {
        String cp = "";

        // for( Artifact artifact : artifacts )
        // {
        // String path = artifact.getFile().getAbsolutePath();
        //
        // cp += ";" + path;
        // }

        return cp.length() > 0 ? cp.substring( 1 ) : cp;
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
        JavadocBean javadoc = new JavadocBean();

        javadoc.addArgument( "-private" );

        javadoc.addArgumentPair( "d", outputDirectory );

        javadoc.addArgumentPair( "sourcepath", srcDir );

        javadoc.addArgumentPair( "classpath", getProjectClasspath() );

        javadoc.addArgumentPair( "doclet", "jdiff.JDiff" );

        javadoc.addArgumentPair( "docletpath", getPluginClasspath() );

        javadoc.addArgumentPair( "oldapi", oldApi );

        javadoc.addArgumentPair( "newapi", newApi );

        javadoc.addArgument( "-stats" );

        javadoc.addArgument( packages );

        javadoc.execute( outputDirectory );
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
        return outputDirectory;
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

}