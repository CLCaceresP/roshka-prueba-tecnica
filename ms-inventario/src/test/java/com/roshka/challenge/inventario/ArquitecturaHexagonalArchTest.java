package com.roshka.challenge.inventario;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;

@AnalyzeClasses(
    packages = "com.roshka.challenge.inventario",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaHexagonalArchTest {

  private static final String BASE = "com.roshka.challenge.inventario";

  @ArchTest
  static final ArchRule capas_dependen_solo_hacia_adentro =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .withOptionalLayers(true)
          .layer("Dominio")
          .definedBy(BASE + ".dominio..")
          .layer("Aplicacion")
          .definedBy(BASE + ".aplicacion..")
          .layer("AdaptadoresEntrada")
          .definedBy(BASE + ".adaptadores.entrada..")
          .layer("AdaptadoresSalida")
          .definedBy(BASE + ".adaptadores.salida..")
          .layer("Configuracion")
          .definedBy(BASE + ".configuracion..")
          .whereLayer("AdaptadoresEntrada")
          .mayOnlyBeAccessedByLayers("Configuracion")
          .whereLayer("AdaptadoresSalida")
          .mayOnlyBeAccessedByLayers("Configuracion")
          .whereLayer("Aplicacion")
          .mayOnlyBeAccessedByLayers("AdaptadoresEntrada", "AdaptadoresSalida", "Configuracion")
          .whereLayer("Dominio")
          .mayOnlyBeAccessedByLayers(
              "Aplicacion", "AdaptadoresEntrada", "AdaptadoresSalida", "Configuracion")
          .whereLayer("Configuracion")
          .mayNotBeAccessedByAnyLayer();

  @ArchTest
  static final ArchRule dominio_esta_libre_de_frameworks =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".dominio..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta..",
              "org.hibernate..",
              "tools.jackson..",
              "com.fasterxml..",
              "org.apache.kafka..",
              "org.redisson..",
              "io.micrometer..",
              "com.roshka.challenge.contrato..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule aplicacion_no_usa_infraestructura =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".aplicacion..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "jakarta.persistence..",
              "org.hibernate..",
              "org.springframework.data..",
              "org.springframework.kafka..",
              "org.springframework.web..",
              "org.springframework.http..",
              "org.apache.kafka..",
              "org.redisson..",
              "tools.jackson..",
              "io.micrometer..")
          .allowEmptyShould(true);

  /**
   * Los adaptadores de entrada llegan a la aplicación solo a través de sus puertos y de los tipos
   * de sus argumentos.
   */
  @ArchTest
  static final ArchRule adaptadores_de_entrada_usan_solo_puertos =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".adaptadores.entrada..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".aplicacion", BASE + ".aplicacion.saga..");

  /**
   * Los controllers y listeners traducen: ni el reloj, ni los puertos de salida, ni la
   * configuración de consultas (límites, zona) son cosa suya.
   */
  @ArchTest
  static final ArchRule adaptadores_de_entrada_no_manejan_politica_de_consulta =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".adaptadores.entrada..")
          .should()
          .dependOnClassesThat(
              JavaClass.Predicates.equivalentTo(Clock.class)
                  .or(
                      JavaClass.Predicates.resideInAPackage(BASE + ".aplicacion.puertos.salida..")
                          .and(
                              DescribedPredicate.not(
                                  JavaClass.Predicates.assignableTo(Throwable.class))))
                  .or(
                      JavaClass.Predicates.resideInAPackage(BASE + ".aplicacion.consulta..")
                          .and(JavaClass.Predicates.simpleNameStartingWith("Configuracion"))))
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_hay_nombres_genericos =
      noClasses()
          .should()
          .haveSimpleNameEndingWith("Impl")
          .orShould()
          .haveSimpleNameEndingWith("Manager")
          .orShould()
          .haveSimpleNameEndingWith("Gestor")
          .orShould()
          .haveSimpleNameEndingWith("Helper")
          .orShould()
          .haveSimpleNameEndingWith("Ayudante")
          .orShould()
          .haveSimpleNameEndingWith("Util")
          .orShould()
          .haveSimpleNameEndingWith("Utils")
          .orShould()
          .haveSimpleNameEndingWith("Utilidades")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule las_interfaces_son_puertos =
      classes()
          .that()
          .areInterfaces()
          .and()
          .areNotNestedClasses()
          .and()
          .resideOutsideOfPackage(BASE + ".dominio..")
          .should()
          .resideInAPackage(BASE + ".aplicacion.puertos..")
          .allowEmptyShould(true);
}
