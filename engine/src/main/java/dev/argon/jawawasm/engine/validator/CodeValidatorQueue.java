package dev.argon.jawawasm.engine.validator;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;

class CodeValidatorQueue {
	private static final int BATCH_SIZE = 20;

	private final Object lock = new Object();
	private int jobThreads;
	private int waitingThreads;
	private final ArrayDeque<CodeValidateJob> queue;
	private final class ParallelWorker implements Worker {
		private final ArrayDeque<CodeValidateJob> jobs = new ArrayDeque<>();

		@Override
		public void add(CodeValidateJob job) {
			if(error != null)
				return;
			jobs.addLast(job);
			if(jobs.size() >= BATCH_SIZE) {
				synchronized (lock) {
					if(error == null) {
						for(int i = 0; i < BATCH_SIZE / 2; ++i) {
							queue.add(jobs.removeFirst());
						}
						if(waitingThreads > 0)
							lock.notifyAll();
					}
				}
			}
		}
	}
	private volatile @Nullable Throwable error;
	private boolean started;

	CodeValidatorQueue(List<CodeValidateJob> jobs) {
		queue = new ArrayDeque<>(jobs);
	}

	public void run() throws ValidationException {
		if(started)
			throw new IllegalStateException("Validation queue already started");
		started = true;
		if(queue.size() < BATCH_SIZE) {
			Worker worker = queue::addLast;
			for(;;) {
				var job = queue.pollFirst();
				if(job == null)
					return;
				runJob(job, worker);
			}
		}
		else {
			runParallel();
		}
	}

	private void runParallel() throws ValidationException {
		boolean interrupted = false;
		synchronized (lock) {
			for(int i = 0; i < Runtime.getRuntime().availableProcessors(); ++i) {
				++jobThreads;
				try {
					var worker = new ParallelWorker();
					Thread.startVirtualThread(() -> jobThread(worker));
				}
				catch(RuntimeException | Error e) {
					--jobThreads;
					error = e;
					queue.clear();
					break;
				}
			}

			while (jobThreads > 0) {
				try {
					lock.wait();
				}
				catch(InterruptedException e) {
					interrupted = true;
					if(error == null)
						error = e;
					queue.clear();
					lock.notifyAll();
				}
			}
		}
		if(interrupted)
			Thread.currentThread().interrupt();
		var failure = error;
		if(failure instanceof ValidationException ex)
			throw ex;
		if(failure instanceof RuntimeException ex)
			throw ex;
		if(failure instanceof Error ex)
			throw ex;
		if(failure != null)
			throw new IllegalStateException("Code validation interrupted", failure);
	}

	private void jobThread(ParallelWorker worker) {
		try {
			for(;;) {
				if(error != null)
					return;
				var job = worker.jobs.pollLast();
				if(job == null) {
					synchronized (lock) {
						for(;;) {
							if(error != null)
								return;
							job = takeWork(worker);
							if(job != null)
								break;
							if(jobThreads == waitingThreads + 1)
								return;
							++waitingThreads;
							try {
								lock.wait();
							}
							finally {
								--waitingThreads;
							}
						}
					}
				}
				runJob(job, worker);
			}
		}
		catch(Throwable e) {
			synchronized (lock) {
				if(error == null)
					error = e;
				queue.clear();
				lock.notifyAll();
			}
		}
		finally {
			synchronized (lock) {
				--jobThreads;
				lock.notifyAll();
			}
		}
	}

	private @Nullable CodeValidateJob takeWork(ParallelWorker worker) {
		var job = worker.jobs.pollLast();
		if(job != null)
			return job;
		job = queue.poll();
		if(job != null) {
			for(int i = 1; i < BATCH_SIZE / 2; ++i) {
				var next = queue.poll();
				if(next == null)
					break;
				worker.jobs.addLast(next);
			}
			return job;
		}

		return null;
	}

	private void runJob(CodeValidateJob job, Worker worker) throws ValidationException {
		var validator = new InstrValidator(job.context(), worker);
		if(job.requireConstant())
			validator.requireConstantExpr(job.body());
		validator.validateInstructions(job.body(), job.argTypes(), job.resultType());
	}
}
