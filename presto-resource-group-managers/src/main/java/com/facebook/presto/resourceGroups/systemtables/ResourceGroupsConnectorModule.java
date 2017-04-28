/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.resourceGroups.systemtables;

import com.facebook.presto.spi.SystemTable;
import com.facebook.presto.spi.procedure.Procedure;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.MultibindingsScanner;
import com.google.inject.multibindings.ProvidesIntoSet;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class ResourceGroupsConnectorModule
    implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.install(MultibindingsScanner.asModule());
        binder.bind(ResourceGroupsConnector.class).in(Scopes.SINGLETON);
        binder.bind(ResourceGroupsSplitManager.class).in(Scopes.SINGLETON);
        binder.bind(ResourceGroupsMetadata.class).in(Scopes.SINGLETON);
        binder.bind(ResourceGroupsRecordSetProvider.class).in(Scopes.SINGLETON);
        Multibinder<SystemTable> tableBinder = newSetBinder(binder, SystemTable.class);
        tableBinder.addBinding().to(ResourceGroupsInfoSystemTable.class).in(Scopes.SINGLETON);
        tableBinder.addBinding().to(ResourceGroupSelectorsSystemTable.class).in(Scopes.SINGLETON);
        tableBinder.addBinding().to(ResourceGroupSpecSystemTable.class).in(Scopes.SINGLETON);
        tableBinder.addBinding().to(ResourceGroupsParentChildSystemTable.class).in(Scopes.SINGLETON);
        tableBinder.addBinding().to(ResourceGroupSpecSystemTable.class).in(Scopes.SINGLETON);
        tableBinder.addBinding().to(ResourceGroupGlobalPropertiesSystemTable.class).in(Scopes.SINGLETON);
        tableBinder.addBinding().to(ConfiguredGroupsSystemTable.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder, Procedure.class);
        binder.bind(AddResourceGroupProcedure.class).in(Scopes.SINGLETON);
        binder.bind(AlterResourceGroupProcedure.class).in(Scopes.SINGLETON);
        binder.bind(RemoveResourceGroupProcedure.class).in(Scopes.SINGLETON);
        binder.bind(ReloadResourceGroupsProcedure.class).in(Scopes.SINGLETON);
        binder.bind(AddSelectorProcedure.class).in(Scopes.SINGLETON);
        binder.bind(AlterSelectorProcedure.class).in(Scopes.SINGLETON);
        binder.bind(RemoveSelectorProcedure.class).in(Scopes.SINGLETON);
        binder.bind(SetCpuQuotaPeriodProcedure.class).in(Scopes.SINGLETON);
    }

    @ProvidesIntoSet
    public static Procedure getAddResourceGroupProcedure(AddResourceGroupProcedure procedure)
    {
        return procedure.getProcedure();
    }

    @ProvidesIntoSet
    public static Procedure getAlterResourceGroupProcedure(AlterResourceGroupProcedure procedure)
    {
        return procedure.getProcedure();
    }

    @ProvidesIntoSet
    public static Procedure getRemoveResourceGroupProcedure(RemoveResourceGroupProcedure procedure)
    {
        return procedure.getProcedure();
    }

    @ProvidesIntoSet
    public static Procedure getAddSelectorProcedure(AddSelectorProcedure procedure)
    {
        return procedure.getProcedure();
    }

    @ProvidesIntoSet
    public static Procedure getRemoveSelectorProcedure(RemoveSelectorProcedure procedure)
    {
        return procedure.getProcedure();
    }

    @ProvidesIntoSet
    public static Procedure getAlterSelectorProcedure(AlterSelectorProcedure procedure)
    {
        return procedure.getProcedure();
    }

    @ProvidesIntoSet
    public static Procedure getReloadProcedure(ReloadResourceGroupsProcedure procedure)
    {
        return procedure.getProcedure();
    }

    @ProvidesIntoSet
    public static Procedure getSetCpuQuotaPeriod(SetCpuQuotaPeriodProcedure procedure)
    {
        return procedure.getProcedure();
    }
}
