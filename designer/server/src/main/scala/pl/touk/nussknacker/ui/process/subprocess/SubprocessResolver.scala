package pl.touk.nussknacker.ui.process.subprocess

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category

class SubprocessResolver(subprocessRepository: SubprocessRepository) {

  def resolveSubprocesses(process: CanonicalProcess, category: Category): ValidatedNel[ProcessCompilationError, CanonicalProcess] = {
    val subprocesses = subprocessRepository.loadSubprocesses(Map.empty, category).map(s => s.canonical.id -> s.canonical).toMap.get _
    pl.touk.nussknacker.engine.compile.SubprocessResolver(subprocesses).resolve(process)
  }

}
