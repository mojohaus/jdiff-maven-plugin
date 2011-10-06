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

import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.command.checkout.CheckOutScmResult;
import org.apache.maven.scm.command.update.UpdateScmResult;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.provider.ScmProvider;
import org.apache.maven.scm.repository.ScmRepository;


public class ScmBean
{
    private ScmManager manager;

    private String connectionUrl;

    public ScmBean( ScmManager manager, String connectionUrl )
    {
        this.manager = manager;
        
        this.connectionUrl = connectionUrl;
    }

    public void checkout( String outputDirectory )
        throws ScmException
    {
        checkout( outputDirectory, null, null );
    }

    public void checkout( String outputDirectory, String includes, String excludes )
        throws ScmException
    {
        try
        {
            ScmRepository repository = getScmRepository( manager, connectionUrl );

            ScmProvider provider = manager.getProviderByRepository( repository );

            ScmFileSet fileSet = getFileSet( outputDirectory, includes, excludes );

            CheckOutScmResult result = provider.checkOut( repository, fileSet );

            if ( !checkResult( result ) ) 
            {
                throw new ScmException( "checkout failed with provider message" );
            }
        }
        catch( Exception ex )
        {
            throw new ScmException( "checkout failed.", ex );
        }
    }
    
    public void update( String targetDirectory )
        throws ScmException
    {
        update( targetDirectory, null, null );
    }
    
    public void update( String targetDirectory, String includes, String excludes  )
        throws ScmException
    {
        try
        {
            ScmRepository repository = getScmRepository( manager, connectionUrl );

            ScmProvider provider = manager.getProviderByRepository( repository );
            
            ScmFileSet fileSet = getFileSet( targetDirectory, includes, excludes );
            
            UpdateScmResult result = provider.update( repository, fileSet );

            if ( !checkResult( result ) )
            {
                throw new ScmException( "checkout failed with provider message" );
            }
        }
        catch( Exception ex )
        {
            throw new ScmException( "checkout failed.", ex );
        }
    }
    
    private ScmRepository getScmRepository( ScmManager manager, String connectionUrl )
        throws ScmException
    {
        return  manager.makeScmRepository( connectionUrl );
    }

    private ScmFileSet getFileSet( String path, String includes, String excludes ) throws IOException
    {
        File dir = new File( path );
        
        if ( includes != null || excludes != null )
        {
            return new ScmFileSet( dir, includes, excludes );
        }
        else
        {
            return new ScmFileSet( dir );
        }
    }

    private boolean checkResult( ScmResult result )
    {
        if ( !result.isSuccess() )
        {
            System.err.println( "Provider message:" );

            System.err.println( result.getProviderMessage() == null ? "" : result.getProviderMessage() );

            System.err.println( "Command output:" );

            System.err.println( result.getCommandOutput() == null ? "" : result.getCommandOutput() );
            
            return false;
        }
        else return true;
    }
}