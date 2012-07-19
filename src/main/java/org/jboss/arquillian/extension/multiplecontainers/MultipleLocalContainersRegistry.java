/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.arquillian.extension.multiplecontainers;

import java.util.*;
import org.jboss.arquillian.config.descriptor.api.ContainerDef;
import org.jboss.arquillian.container.impl.ContainerCreationException;
import org.jboss.arquillian.container.impl.ContainerImpl;
import org.jboss.arquillian.container.impl.LocalContainerRegistry;
import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.ContainerRegistry;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.deployment.TargetDescription;
import org.jboss.arquillian.core.api.Injector;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.core.spi.Validate;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class MultipleLocalContainersRegistry implements ContainerRegistry {
  private List<Container> containers;
   
   private Injector injector;

   public MultipleLocalContainersRegistry(Injector injector)
   {
      this.containers = new ArrayList<Container>();
      this.injector = injector;
   }
   
   /* (non-Javadoc)
    * @see org.jboss.arquillian.impl.domain.ContainerRegistryA#create(org.jboss.arquillian.impl.configuration.api.ContainerDef, org.jboss.arquillian.core.spi.ServiceLoader)
    */
   @Override
   public Container create(ContainerDef definition, ServiceLoader loader)
   {
      Validate.notNull(definition, "Definition must be specified");

      try
      {
         // TODO: this whole Classloading thing is a HACK and does not work. Need to split out into multiple JVMs for multi container testing
//         ClassLoader containerClassLoader;
//         if(definition.getDependencies().size() > 0)
//         {
//            final MavenDependencyResolver resolver = DependencyResolvers.use(MavenDependencyResolver.class).artifacts(
//                  definition.getDependencies().toArray(new String[0]));
//            
//            URL[] resolvedURLs = MapObject.convert(resolver.resolveAsFiles());
//
//            containerClassLoader = new FilteredURLClassLoader(resolvedURLs, "org.jboss.(arquillian|shrinkwrap)..*");
//         }
//         else
//         {
//            containerClassLoader = LocalContainerRegistry.class.getClassLoader();
//         }
//            
          
          System.out.println("Registering container: " + definition.getContainerName());
          
         Map<String,String> props = definition.getContainerProperties();
         if (! props.containsKey("adapterImplClass"))
             throw new Exception("Container adapter implementation class must be provided via 'adapterImplClass' property.");

         Class dcImplClass = Class.forName(props.get("adapterImplClass"));
         
         Collection<DeployableContainer> services = loader.all(DeployableContainer.class);        
         
         DeployableContainer dcService = null;
         for(DeployableContainer dc : services) {
             if (dcImplClass.isInstance(dc)) {
                 dcService = dc;
                 break;
             }
         }
         
         if (dcService == null) throw new Exception ("No suitable container adapter implementation found.");
         
         return addContainer(
               //before a Container is added to a collection of containers, inject into its injection point
               injector.inject(new ContainerImpl(
                               definition.getContainerName(), 
                               dcService,
                               definition)));
      }
      catch (Exception e) 
      {
         throw new ContainerCreationException("Could not create Container " + definition.getContainerName(), e);
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.impl.domain.ContainerRegistryA#getContainer(java.lang.String)
    */
   @Override
   public Container getContainer(String name)
   {
      return findMatchingContainer(name);
   }
   
   /* (non-Javadoc)
    * @see org.jboss.arquillian.impl.domain.ContainerRegistryA#getContainers()
    */
   @Override
   public List<Container> getContainers()
   {
      return Collections.unmodifiableList(new ArrayList<Container>(containers));
   }
   
   /* (non-Javadoc)
    * @see org.jboss.arquillian.impl.domain.ContainerRegistryA#getContainer(org.jboss.arquillian.spi.client.deployment.TargetDescription)
    */
   @Override
   public Container getContainer(TargetDescription target)
   {
      Validate.notNull(target, "Target must be specified");
      if(TargetDescription.DEFAULT.equals(target))
      {
         return findDefaultContainer();
      }
      return findMatchingContainer(target.getName());
   }

   private Container addContainer(Container contianer)
   {
      containers.add(contianer);
      return contianer;
   }
   
   /**
    * @return
    */
   private Container findDefaultContainer()
   {
      if(containers.size() == 1)
      {
         return containers.get(0);
      }
      for(Container container : containers)
      {
        if(container.getContainerConfiguration().isDefault())
        {
           return container;
        }
      }
      return null;
   }

   private Container findMatchingContainer(String name)
   {
      for(Container container: containers)
      {
         if(container.getName().equals(name))
         {
            return container;
         }
      }
      return null;
   } 
   
}
