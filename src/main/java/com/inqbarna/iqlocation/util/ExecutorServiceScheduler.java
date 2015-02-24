package com.inqbarna.iqlocation.util;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.internal.schedulers.ScheduledAction;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

/**
 * Created by David García <david.garcia@inqbarna.com> on 23/2/15.
 */
public class ExecutorServiceScheduler extends Scheduler {

    private ScheduledExecutorService service;

    public ExecutorServiceScheduler(ScheduledExecutorService service) {
        this.service = service;
    }

    @Override
    public Worker createWorker() {
        return new InnerWorker(service);
    }

    private static class InnerWorker extends Worker {

        private ScheduledExecutorService service;
        private ScheduledFuture<?> sched;

        private InnerWorker(ScheduledExecutorService service) {
            this.service = service;
        }

        private CompositeSubscription compositeSubscription = new CompositeSubscription();

        @Override
        public Subscription schedule(Action0 action) {
            return schedule(action, 0, TimeUnit.SECONDS);
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            final ScheduledAction scheduledAction = new ScheduledAction(action);
            scheduledAction.add(
                    Subscriptions.create(
                            new Action0() {
                                @Override
                                public void call() {
                                    if (null != sched) {
                                        sched.cancel(false);
                                        sched = null;
                                    }
                                }
                            }));
            scheduledAction.addParent(compositeSubscription);
            compositeSubscription.add(scheduledAction);


            sched = service.schedule(scheduledAction, delayTime, unit);

            return scheduledAction;
        }

        @Override
        public void unsubscribe() {
            compositeSubscription.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return compositeSubscription.isUnsubscribed();
        }
    }
}
