package org.apache.maven.plugin.jdiff;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.FileUtils;

public class JDiffUtils
{
    private JDiffUtils()
    {
        // hide constructor of utility class
    }
    
    public static List<String> getProjectSourceRoots( MavenProject p )
    {
        if ( "pom".equals( p.getPackaging().toLowerCase() ) )
        {
            return Collections.emptyList();
        }

        return ( p.getCompileSourceRoots() == null ? Collections.EMPTY_LIST
                        : new LinkedList( p.getCompileSourceRoots() ) );
    }

    public static List<String> getClasspathElements( MavenProject project )
        throws MavenReportException
    {
        List<String> classpathElements = new ArrayList<String>();

        for ( Artifact a : (List<Artifact>) project.getCompileArtifacts() )
        {
            classpathElements.add( a.getFile().getPath() );
        }
        
        return classpathElements;
    }
    
    public static Set<String> getPackages( MavenProject project ) 
    {
        Set<String> packages = new HashSet<String>();
        List<String> compileRoots = project.getCompileSourceRoots(); 
        for ( String compileRoot : compileRoots  )
        {
            try
            {
                List<String> files = FileUtils.getFileNames(  FileUtils.resolveFile( project.getBasedir(), compileRoot), "**/*.java", null, false );
                for ( String file : files )
                {
                    packages.add( FileUtils.dirname( file ).replace( File.separatorChar, '.' ) );
                }
            }
            catch ( IOException e )
            {
                //do nothing
            }
        }
        
        return packages;
            
    }
}
