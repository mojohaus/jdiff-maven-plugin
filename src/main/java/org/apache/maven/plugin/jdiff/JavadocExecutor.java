package org.apache.maven.plugin.jdiff;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;

import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.DefaultConsumer;

/**
 * Execute javadoc 
 */
public class JavadocExecutor
{
    private Commandline cmd = new Commandline();
    
    public JavadocExecutor( String executable )
    {
        cmd.setExecutable( executable );
    }
    
    public void addArgumentPair( String argKey, String argValue )
    {
        cmd.createArg().setValue( "-" + argKey );
        
        cmd.createArg().setValue( argValue );
    }
    
    public void addArgument( String arg )
    {
        cmd.createArg().setValue( arg );
    }
    
    public void execute( String workingDir ) throws MavenReportException
    {
        File dir = new File( workingDir );
        
        if ( !dir.exists() ) 
        {
            dir.mkdirs();
        }
        
        cmd.setWorkingDirectory( dir.getAbsolutePath() );
        
        int exitCode = 0;
                
        try
        {
            System.out.print( cmd.toString() );
            exitCode = CommandLineUtils.executeCommandLine( cmd, 
                                                            new DefaultConsumer(), 
                                                            new DefaultConsumer() );
        }
        catch ( Exception ex )
        {
            throw new MavenReportException( "generateJDiff doclet failed.", ex );
        }
        
        if ( exitCode != 0 )
        {
            throw new MavenReportException( "generate JDiff doclet failed." );
        }
    }
}