package org.apache.maven.plugin.jdiff;

public class JavadocExecutionException
    extends Exception
{

    public JavadocExecutionException( String message )
    {
        super( message );
    }

    public JavadocExecutionException( String message, Throwable cause )
    {
        super( message, cause );
    }
}
